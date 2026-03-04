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
import java.util.HashMap;
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

    private static final LocalDateTime DEFAULT_END = LocalDateTime.now().plusYears(100);

    // паблик
    @Override
    public List<EventShortDto> getPublishedEvents(String text, List<Long> categories, Boolean paid,
                                                  LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                                  Boolean onlyAvailable, EventSort sort,
                                                  int from, int size, HttpServletRequest request) {

        log.info("========== getPublishedEvents START ==========");
        log.info("from={}, size={}", from, size);
        log.info("text={}, categories={}, paid={}", text, categories, paid);
        log.info("rangeStart={}, rangeEnd={}", rangeStart, rangeEnd);
        log.info("onlyAvailable={}, sort={}", onlyAvailable, sort);

        String safeText = text == null ? "" : text.trim();

        boolean categoriesEmpty = categories == null || categories.isEmpty();
        List<Long> safeCategories = categoriesEmpty ? Collections.emptyList() : categories;

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

        Pageable pageable;
        if (sort == EventSort.VIEWS) {
            pageable = PageRequest.of(from / size, size);
        } else {
            Sort sortBy = Sort.by(Sort.Direction.ASC, "eventDate");
            pageable = PageRequest.of(from / size, size, sortBy);
        }

        List<Event> events = eventRepository.findPublishedEvents(
                safeText,
                safeCategories,
                categoriesEmpty,
                paid,
                rangeStart,
                rangeEnd,
                safeOnlyAvailable,
                Event.EventState.PUBLISHED,
                ParticipationRequest.RequestStatus.CONFIRMED,
                pageable
        );

        log.info("Найдено событий в БД: {}", events.size());

        if (events.isEmpty()) {
            log.info("Событий нет, возвращаем пустой список");
            return Collections.emptyList();
        }

        List<String> uris = events.stream()
                .map(e -> "/events/" + e.getId())
                .collect(Collectors.toList());

        // минимльная дата
        LocalDateTime earliestEventDate = events.stream()
                .map(Event::getCreatedOn)
                .min(LocalDateTime::compareTo)
                .orElse(rangeStart);

        Map<String, Long> viewsMap;
        try {
            viewsMap = statsService.getViewsMap(
                    earliestEventDate,
                    LocalDateTime.now(),
                    uris);
            log.info("Загружено просмотров для {} событий", events.size());
        } catch (Exception e) {
            log.error("Ошибка при получении статистики: {}", e.getMessage());
            viewsMap = Collections.emptyMap();
        }

        Map<Long, Long> confirmedMap = getConfirmedRequestsMap(events);
        log.info("Загружено подтверждённых запросов для {} событий", confirmedMap.size());

        Map<String, Long> finalViewsMap = viewsMap;
        List<EventShortDto> result = events.stream()
                .map(event -> EventMapper.toShortDto(
                        event,
                        confirmedMap.getOrDefault(event.getId(), 0L),
                        finalViewsMap.getOrDefault("/events/" + event.getId(), 0L)))
                .collect(Collectors.toList());

        if (sort == EventSort.VIEWS) {
            result.sort((a, b) -> Long.compare(b.getViews(), a.getViews()));
            log.info("Отсортировано по просмотрам (убывание)");
        }

        log.info("Возвращаем {} событий", result.size());
        log.info("========== getPublishedEvents END ==========");
        return result;
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
            log.info("Хит сохранён для события id={}", id);
        } catch (Exception e) {
            log.error("Ошибка при сохранении статистики: {}", e.getMessage());
        }

        Long views = 0L;
        try {
            // дата события для запроса статистики
            Map<String, Long> viewsMap = statsService.getUniqueViewsMap(
                    event.getCreatedOn(),
                    LocalDateTime.now(),
                    List.of("/events/" + id));
            views = viewsMap.getOrDefault("/events/" + id, 0L);
            log.info("Просмотры для события id={}: {}", id, views);
        } catch (Exception e) {
            log.error("Ошибка при получении статистики: {}", e.getMessage());
        }

        long confirmed = getConfirmedRequestsCount(id);

        return EventMapper.toFullDto(event, confirmed, views);
    }

    // админ
    @Override
    public List<EventFullDto> searchEventsByAdmin(List<Long> users, List<Event.EventState> states,
                                                  List<Long> categories, LocalDateTime rangeStart,
                                                  LocalDateTime rangeEnd, int from, int size) {

        if (rangeStart == null) rangeStart = LocalDateTime.now().minusYears(100);
        if (rangeEnd == null) rangeEnd = DEFAULT_END;
        if (size <= 0) size = 10;
        if (from < 0) from = 0;

        Pageable pageable = PageRequest.of(from / size, size);

        List<Event> events = eventRepository.findEventsByAdmin(
                users, states, categories, rangeStart, rangeEnd, pageable);

        if (events.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> uris = events.stream()
                .map(e -> "/events/" + e.getId())
                .collect(Collectors.toList());

        // минимальная дату создания события
        LocalDateTime earliestEventDate = events.stream()
                .map(Event::getCreatedOn)
                .min(LocalDateTime::compareTo)
                .orElse(rangeStart);

        Map<String, Long> viewsMap;
        try {
            viewsMap = statsService.getViewsMap(
                    earliestEventDate,
                    LocalDateTime.now(),
                    uris);
        } catch (Exception e) {
            log.error("Ошибка при получении статистики: {}", e.getMessage());
            viewsMap = Collections.emptyMap();
        }

        Map<Long, Long> confirmedMap = getConfirmedRequestsMap(events);

        Map<String, Long> finalViewsMap = viewsMap;
        return events.stream()
                .map(event -> EventMapper.toFullDto(
                        event,
                        confirmedMap.getOrDefault(event.getId(), 0L),
                        finalViewsMap.getOrDefault("/events/" + event.getId(), 0L)))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public EventFullDto moderateEventByAdmin(Long eventId, UpdateEventAdminRequest dto) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Событие с id=" + eventId + " не найдено"));

        validateEventDateNotPast(dto.getEventDate());

        EventStateAction action = dto.getStateAction() != null ?
                EventStateAction.valueOf(dto.getStateAction()) : null;

        if (action == EventStateAction.PUBLISH_EVENT) {
            if (event.getState() != Event.EventState.PENDING) {
                throw new ConflictException("Нельзя опубликовать событие в статусе " + event.getState());
            }

            LocalDateTime dateForValidation = dto.getEventDate() != null ? dto.getEventDate() : event.getEventDate();
            validateEventDateForAdmin(dateForValidation, action);
        }

        if (action == EventStateAction.REJECT_EVENT) {
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

        Long views = 0L;
        try {
            // дата создания события
            Map<String, Long> viewsMap = statsService.getViewsMap(
                    event.getCreatedOn(),
                    LocalDateTime.now(),
                    List.of("/events/" + eventId));
            views = viewsMap.getOrDefault("/events/" + eventId, 0L);
        } catch (Exception e) {
            log.error("Ошибка при получении статистики: {}", e.getMessage());
        }

        long confirmed = getConfirmedRequestsCount(eventId);

        return EventMapper.toFullDto(event, confirmed, views);
    }

    private void validateEventDateForAdmin(LocalDateTime eventDate, EventStateAction stateAction) {
        if (stateAction == EventStateAction.PUBLISH_EVENT && eventDate != null) {
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

    // пользователь
    @Override
    public List<EventShortDto> getUserEvents(Long userId, int from, int size) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("Пользователь с id=" + userId + " не найден");
        }

        if (size <= 0) size = 10;
        if (from < 0) from = 0;

        Pageable pageable = PageRequest.of(from / size, size);
        List<Event> events = eventRepository.findAllByInitiatorId(userId, pageable).getContent();

        if (events.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> uris = events.stream()
                .map(e -> "/events/" + e.getId())
                .collect(Collectors.toList());

        // минимальная дату создания события
        LocalDateTime earliestEventDate = events.stream()
                .map(Event::getCreatedOn)
                .min(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now().minusYears(1));

        Map<String, Long> viewsMap;
        try {
            viewsMap = statsService.getViewsMap(
                    earliestEventDate,
                    LocalDateTime.now(),
                    uris);
        } catch (Exception e) {
            log.error("Ошибка при получении статистики: {}", e.getMessage());
            viewsMap = Collections.emptyMap();
        }

        Map<Long, Long> confirmedMap = getConfirmedRequestsMap(events);

        Map<String, Long> finalViewsMap = viewsMap;
        return events.stream()
                .map(event -> EventMapper.toShortDto(
                        event,
                        confirmedMap.getOrDefault(event.getId(), 0L),
                        finalViewsMap.getOrDefault("/events/" + event.getId(), 0L)))
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

        return EventMapper.toFullDto(event, 0L, 0L);
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

        Long views = 0L;
        try {
            // дата создания события
            Map<String, Long> viewsMap = statsService.getViewsMap(
                    event.getCreatedOn(),
                    LocalDateTime.now(),
                    List.of("/events/" + eventId));
            views = viewsMap.getOrDefault("/events/" + eventId, 0L);
        } catch (Exception e) {
            log.error("Ошибка при получении статистики: {}", e.getMessage());
        }

        long confirmed = getConfirmedRequestsCount(eventId);

        return EventMapper.toFullDto(event, confirmed, views);
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

        EventStateAction action = dto.getStateAction() != null ?
                EventStateAction.valueOf(dto.getStateAction()) : null;

        if (action == EventStateAction.SEND_TO_REVIEW) {
            event.setState(Event.EventState.PENDING);
        } else if (action == EventStateAction.CANCEL_REVIEW) {
            event.setState(Event.EventState.CANCELED);
        }

        Category category = null;
        if (dto.getCategory() != null) {
            category = categoryRepository.findById(dto.getCategory())
                    .orElseThrow(() -> new NotFoundException("Категория с id=" + dto.getCategory() + " не найдена"));
        }

        event = EventMapper.toEntity(dto, event, category);
        event = eventRepository.save(event);

        Long views = 0L;
        try {
            // дата создания события
            Map<String, Long> viewsMap = statsService.getViewsMap(
                    event.getCreatedOn(),
                    LocalDateTime.now(),
                    List.of("/events/" + eventId));
            views = viewsMap.getOrDefault("/events/" + eventId, 0L);
        } catch (Exception e) {
            log.error("Ошибка при получении статистики: {}", e.getMessage());
        }

        long confirmed = getConfirmedRequestsCount(eventId);

        return EventMapper.toFullDto(event, confirmed, views);
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
        if (requests.isEmpty()) {
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

        long confirmedRequests = getConfirmedRequestsCount(eventId);
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

    private long getConfirmedRequestsCount(Long eventId) {
        try {
            Long count = requestRepository.countByEventIdAndStatus(
                    eventId, ParticipationRequest.RequestStatus.CONFIRMED);
            return count != null ? count : 0L;
        } catch (Exception e) {
            log.error("Ошибка при получении confirmedRequests для события {}: {}", eventId, e.getMessage());
            return 0L;
        }
    }

    private Map<Long, Long> getConfirmedRequestsMap(List<Event> events) {
        if (events == null || events.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            List<Long> eventIds = events.stream()
                    .map(Event::getId)
                    .collect(Collectors.toList());

            List<ParticipationRequestRepository.EventConfirmedCount> counts =
                    requestRepository.countConfirmedRequestsByEventIds(
                            eventIds, ParticipationRequest.RequestStatus.CONFIRMED);

            Map<Long, Long> result = new HashMap<>();
            for (ParticipationRequestRepository.EventConfirmedCount c : counts) {
                result.put(c.getEventId(), c.getConfirmedRequests());
            }
            return result;
        } catch (Exception e) {
            log.error("Ошибка при получении карты confirmedRequests: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }
}