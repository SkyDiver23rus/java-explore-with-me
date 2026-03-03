package ru.practicum.main.server.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.main.server.dto.*;
import ru.practicum.main.server.exception.BadRequestException;
import ru.practicum.main.server.exception.ConflictException;
import ru.practicum.main.server.exception.NotFoundException;
import ru.practicum.main.server.mapper.EventMapper;
import ru.practicum.main.server.mapper.ParticipationRequestMapper;
import ru.practicum.main.server.model.*;
import ru.practicum.main.server.repository.CategoryRepository;
import ru.practicum.main.server.repository.EventRepository;
import ru.practicum.main.server.repository.ParticipationRequestRepository;
import ru.practicum.main.server.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventServiceImpl implements EventService {
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ParticipationRequestRepository requestRepository;
    private final StatsService statsService;

    private static final LocalDateTime DEFAULT_START = LocalDateTime.now().minusYears(100);
    private static final LocalDateTime DEFAULT_END = LocalDateTime.now().plusYears(100);

    // паблик

    @Override
    public List<EventShortDto> getPublishedEvents(String text, List<Long> categories, Boolean paid,
                                                  LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                                  Boolean onlyAvailable, String sort,
                                                  int from, int size, HttpServletRequest request) {

        log.info("getPublishedEvents: from={}, size={}", from, size);

        if (size <= 0) {
            size = 10;
        }
        if (from < 0) {
            from = 0;
        }

        String safeText = text == null ? "" : text.trim();
        List<Long> safeCategories = categories == null ? Collections.emptyList() : categories;
        boolean categoriesEmpty = safeCategories.isEmpty();
        boolean safeOnlyAvailable = Boolean.TRUE.equals(onlyAvailable);

        if (rangeStart == null) {
            rangeStart = LocalDateTime.now();
        }
        if (rangeEnd == null) {
            rangeEnd = DEFAULT_END;
        }
        if (rangeStart.isAfter(rangeEnd)) {
            throw new BadRequestException("Дата начала не может быть позже даты окончания");
        }

        Sort sortBy = "VIEWS".equals(sort)
                ? Sort.by(Sort.Direction.DESC, "views")
                : Sort.by(Sort.Direction.DESC, "eventDate");

        Pageable pageable = PageRequest.of(from / size, size, sortBy);

        List<Event> events = eventRepository.findPublishedEvents(
                safeText,
                safeCategories,
                categoriesEmpty,
                paid,
                rangeStart,
                rangeEnd,
                safeOnlyAvailable,
                pageable
        );

        if (events == null || events.isEmpty()) {
            return Collections.emptyList();
        }

        return events.stream()
                .map(event -> EventMapper.toShortDto(event, event.getViews() != null ? event.getViews() : 0L))
                .collect(Collectors.toList());
    }

    @Override
    public EventFullDto getPublishedEventById(Long id, HttpServletRequest request) {
        log.info("Получение события id={} для публичного просмотра", id);

        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Событие с id=" + id + " не найдено"));

        if (event.getState() != Event.EventState.PUBLISHED) {
            throw new NotFoundException("Событие с id=" + id + " не найдено");
        }

        try {
            statsService.saveHit(request.getRequestURI(), request.getRemoteAddr());
        } catch (Exception e) {
            log.error("Ошибка при сохранении статистики, но продолжаем: {}", e.getMessage());
        }

        Long oldViews = event.getViews() != null ? event.getViews() : 0L;
        event.setViews(oldViews + 1);
        event = eventRepository.save(event);

        return EventMapper.toFullDto(event, event.getViews());
    }

    // админ

    @Override
    public List<EventFullDto> searchEventsByAdmin(List<Long> users, List<Event.EventState> states,
                                                  List<Long> categories, LocalDateTime rangeStart,
                                                  LocalDateTime rangeEnd, int from, int size) {

        if (rangeStart == null) rangeStart = DEFAULT_START;
        if (rangeEnd == null) rangeEnd = DEFAULT_END;
        if (size <= 0) size = 10;

        Pageable pageable = PageRequest.of(from / size, size);

        List<Event> events = eventRepository.findEventsByAdmin(
                users, states, categories, rangeStart, rangeEnd, pageable);

        if (events == null || events.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> uris = events.stream()
                .map(e -> "/events/" + e.getId())
                .collect(Collectors.toList());

        Map<String, Long> viewsMap = Collections.emptyMap();
        try {
            viewsMap = statsService.getViewsMap(DEFAULT_START, DEFAULT_END, uris);
        } catch (Exception e) {
            log.error("Ошибка при получении статистики: {}", e.getMessage());
        }

        Map<String, Long> finalViewsMap = viewsMap;
        return events.stream()
                .map(event -> EventMapper.toFullDto(
                        event,
                        finalViewsMap.getOrDefault("/events/" + event.getId(),
                                event.getViews() != null ? event.getViews() : 0L)))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public EventFullDto moderateEventByAdmin(Long eventId, UpdateEventAdminRequest dto) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Событие с id=" + eventId + " не найдено"));


        // если админ передал новую дату и она уже наступила -> 400 Bad Request
        validateEventDateNotPast(dto.getEventDate());

        if ("PUBLISH_EVENT".equals(dto.getStateAction())) {
            if (event.getState() != Event.EventState.PENDING) {
                throw new ConflictException("Нельзя опубликовать событие в статусе " + event.getState());
            }

            LocalDateTime dateForValidation = dto.getEventDate() != null ? dto.getEventDate() : event.getEventDate();
            validateEventDateForAdmin(dateForValidation, dto.getStateAction());
        }

        if ("REJECT_EVENT".equals(dto.getStateAction())) {
            if (event.getState() == Event.EventState.PUBLISHED) {
                throw new ConflictException("Нельзя отклонить уже опубликованное событие");
            }
        }

        Category category = null;
        if (dto.getCategory() != null) {
            category = categoryRepository.findById(dto.getCategory())
                    .orElseThrow(() -> new NotFoundException("Категория с id=" + dto.getCategory() + " не найдена"));
        }

        event = EventMapper.toEntity(dto, event, category);
        event = eventRepository.save(event);

        Long views = event.getViews() != null ? event.getViews() : 0L;
        try {
            views = statsService.getViews("/events/" + eventId, DEFAULT_START, DEFAULT_END);
        } catch (Exception e) {
            log.error("Ошибка при получении статистики: {}", e.getMessage());
        }
        return EventMapper.toFullDto(event, views);
    }

    private void validateEventDateForAdmin(LocalDateTime eventDate, String stateAction) {
        if ("PUBLISH_EVENT".equals(stateAction) && eventDate != null) {
            LocalDateTime now = LocalDateTime.now().withNano(0);
            LocalDateTime minPublishDate = now.plusHours(1);
            LocalDateTime dateToCheck = eventDate.withNano(0);

            if (dateToCheck.isBefore(minPublishDate)) {
                throw new ConflictException(
                        "Нельзя опубликовать событие, которое начнется менее чем через час");
            }
        }
    }

    private void validateEventDateNotPast(LocalDateTime eventDate) {
        if (eventDate == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now().withNano(0);
        LocalDateTime dateToCheck = eventDate.withNano(0);

        if (!dateToCheck.isAfter(now)) {
            throw new BadRequestException("Дата события не может быть в прошлом или настоящем");
        }
    }

    //пользователь

    @Override
    public List<EventShortDto> getUserEvents(Long userId, int from, int size) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("Пользователь с id=" + userId + " не найден");
        }

        if (size <= 0) size = 10;
        Pageable pageable = PageRequest.of(from / size, size);
        List<Event> events = eventRepository.findAllByInitiatorId(userId, pageable).getContent();

        if (events == null || events.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> uris = events.stream()
                .map(e -> "/events/" + e.getId())
                .collect(Collectors.toList());

        Map<String, Long> viewsMap = Collections.emptyMap();
        try {
            viewsMap = statsService.getViewsMap(DEFAULT_START, DEFAULT_END, uris);
        } catch (Exception e) {
            log.error("Ошибка при получении статистики: {}", e.getMessage());
        }

        Map<String, Long> finalViewsMap = viewsMap;
        return events.stream()
                .map(event -> EventMapper.toShortDto(event,
                        finalViewsMap.getOrDefault("/events/" + event.getId(),
                                event.getViews() != null ? event.getViews() : 0L)))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public EventFullDto createEvent(Long userId, NewEventDto dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь с id=" + userId + " не найден"));

        Category category = categoryRepository.findById(dto.getCategory())
                .orElseThrow(() -> new NotFoundException("Категория с id=" + dto.getCategory() + " не найдена"));

        if (dto.getEventDate().isBefore(LocalDateTime.now().plusHours(2))) {
            throw new BadRequestException("Дата события должна быть не ранее чем через 2 часа");
        }

        Event event = EventMapper.toEntity(dto, category, user);
        event = eventRepository.save(event);

        return EventMapper.toFullDto(event, 0L);
    }

    @Override
    public EventFullDto getUserEventById(Long userId, Long eventId) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("Пользователь с id=" + userId + " не найден");
        }

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Событие с id=" + eventId + " не найдено"));

        if (!event.getInitiator().getId().equals(userId)) {
            throw new NotFoundException("Событие с id=" + eventId + " не найдено у пользователя " + userId);
        }

        Long views = event.getViews() != null ? event.getViews() : 0L;
        try {
            views = statsService.getViews("/events/" + eventId, DEFAULT_START, DEFAULT_END);
        } catch (Exception e) {
            log.error("Ошибка при получении статистики: {}", e.getMessage());
        }
        return EventMapper.toFullDto(event, views);
    }

    private void validateEventDateForUpdate(LocalDateTime eventDate) {
        if (eventDate == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now().withNano(0);
        LocalDateTime dateToCheck = eventDate.withNano(0);

        if (!dateToCheck.isAfter(now)) {
            throw new BadRequestException("Дата события не может быть в прошлом или настоящем");
        }

        LocalDateTime minEventDate = now.plusHours(2);
        if (dateToCheck.isBefore(minEventDate)) {
            throw new BadRequestException(
                    "Дата события должна быть не ранее чем через 2 часа от текущего момента");
        }
    }

    @Override
    @Transactional
    public EventFullDto updateEventByUser(Long userId, Long eventId, UpdateEventUserRequest dto) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("Пользователь с id=" + userId + " не найден");
        }

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Событие с id=" + eventId + " не найдено"));

        if (!event.getInitiator().getId().equals(userId)) {
            throw new NotFoundException("Событие с id=" + eventId + " не найдено у пользователя " + userId);
        }

        if (event.getState() == Event.EventState.PUBLISHED) {
            throw new ConflictException("Нельзя изменить опубликованное событие");
        }

        validateEventDateForUpdate(dto.getEventDate());

        if ("SEND_TO_REVIEW".equals(dto.getStateAction())) {
            event.setState(Event.EventState.PENDING);
        } else if ("CANCEL_REVIEW".equals(dto.getStateAction())) {
            event.setState(Event.EventState.CANCELED);
        }

        Category category = null;
        if (dto.getCategory() != null) {
            category = categoryRepository.findById(dto.getCategory())
                    .orElseThrow(() -> new NotFoundException("Категория с id=" + dto.getCategory() + " не найдена"));
        }

        event = EventMapper.toEntity(dto, event, category);
        event = eventRepository.save(event);

        Long views = event.getViews() != null ? event.getViews() : 0L;
        try {
            views = statsService.getViews("/events/" + eventId, DEFAULT_START, DEFAULT_END);
        } catch (Exception e) {
            log.error("Ошибка при получении статистики: {}", e.getMessage());
        }
        return EventMapper.toFullDto(event, views);
    }

    @Override
    public List<ParticipationRequestDto> getEventRequests(Long userId, Long eventId) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("Пользователь с id=" + userId + " не найден");
        }

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Событие с id=" + eventId + " не найдено"));

        if (!event.getInitiator().getId().equals(userId)) {
            throw new NotFoundException("Событие с id=" + eventId + " не принадлежит пользователю " + userId);
        }

        List<ParticipationRequest> requests = requestRepository.findAllByEventId(eventId);
        if (requests == null || requests.isEmpty()) {
            return Collections.emptyList();
        }

        return requests.stream()
                .map(ParticipationRequestMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public EventRequestStatusUpdateResult updateRequestStatus(Long userId, Long eventId,
                                                              EventRequestStatusUpdateRequest dto) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("Пользователь с id=" + userId + " не найден");
        }

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Событие с id=" + eventId + " не найдено"));

        if (!event.getInitiator().getId().equals(userId)) {
            throw new NotFoundException("Событие с id=" + eventId + " не принадлежит пользователю " + userId);
        }

        long confirmedRequests = event.getConfirmedRequests() != null ? event.getConfirmedRequests() : 0L;
        long participantLimit = event.getParticipantLimit() != null ? event.getParticipantLimit() : 0L;

        if (participantLimit > 0 && confirmedRequests >= participantLimit) {
            throw new ConflictException("Достигнут лимит участников");
        }

        List<ParticipationRequest> requests = requestRepository.findAllById(dto.getRequestIds());

        for (ParticipationRequest request : requests) {
            if (request.getStatus() != ParticipationRequest.RequestStatus.PENDING) {
                throw new ConflictException("Заявка с id=" + request.getId() + " не в статусе PENDING");
            }
        }

        List<ParticipationRequestDto> confirmed = new ArrayList<>();
        List<ParticipationRequestDto> rejected = new ArrayList<>();

        if ("CONFIRMED".equals(dto.getStatus())) {
            for (ParticipationRequest request : requests) {
                if (participantLimit > 0 && confirmedRequests >= participantLimit) {
                    request.setStatus(ParticipationRequest.RequestStatus.REJECTED);
                    rejected.add(ParticipationRequestMapper.toDto(requestRepository.save(request)));
                } else {
                    request.setStatus(ParticipationRequest.RequestStatus.CONFIRMED);
                    confirmedRequests++;
                    confirmed.add(ParticipationRequestMapper.toDto(requestRepository.save(request)));
                }
            }
            event.setConfirmedRequests(confirmedRequests);
            eventRepository.save(event);

        } else if ("REJECTED".equals(dto.getStatus())) {
            for (ParticipationRequest request : requests) {
                request.setStatus(ParticipationRequest.RequestStatus.REJECTED);
                rejected.add(ParticipationRequestMapper.toDto(requestRepository.save(request)));
            }
        }

        return EventRequestStatusUpdateResult.builder()
                .confirmedRequests(confirmed)
                .rejectedRequests(rejected)
                .build();
    }
}