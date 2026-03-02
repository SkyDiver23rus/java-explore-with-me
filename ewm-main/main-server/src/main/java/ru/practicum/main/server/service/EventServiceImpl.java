package ru.practicum.main.server.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.main.dto.*;
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

    // ПУБЛИЧНЫЕ МЕТОДЫ

    @Override
    public List<EventShortDto> getPublishedEvents(String text, List<Long> categories, Boolean paid,
                                                  LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                                  Boolean onlyAvailable, String sort,
                                                  int from, int size, HttpServletRequest request) {
        log.info("EventService: getPublishedEvents с from={}, size={}", from, size);
        // Устанавливаем диапазон дат по умолчанию
        if (rangeStart == null) {
            rangeStart = LocalDateTime.now();
        }
        if (rangeEnd == null) {
            rangeEnd = DEFAULT_END;
        }

        if (rangeStart.isAfter(rangeEnd)) {
            throw new BadRequestException("Дата начала не может быть позже даты окончания");
        }
        // Сортировка
        Sort sortBy;
        if ("VIEWS".equals(sort)) {
            sortBy = Sort.by(Sort.Direction.DESC, "views");
        } else {
            sortBy = Sort.by(Sort.Direction.DESC, "eventDate");
        }

        int pageNumber = from / size;
        Pageable pageable = PageRequest.of(pageNumber, size, sortBy);
        log.info("EventService: pageNumber={}, pageSize={}", pageNumber, size);

        List<Event> events = eventRepository.findPublishedEvents(
                text, categories, paid, rangeStart, rangeEnd, onlyAvailable, pageable);

        log.info("EventService: найдено событий: {}", events.size());

        return events.stream()
                .map(event -> EventMapper.toShortDto(event, event.getViews()))
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
        log.info("Сохранение хита для события id={}, текущие просмотры={}", id, event.getViews());
        statsService.saveHit(request.getRequestURI(), request.getRemoteAddr());
        // Увеличиваем счётчик просмотров в БД
        event.setViews(event.getViews() + 1);
        event = eventRepository.save(event);
        log.info("После увеличения просмотры={}", event.getViews());
        return EventMapper.toFullDto(event, event.getViews());
    }
    //  МЕТОДЫ ДЛЯ АДМИНА

    @Override
    public List<EventFullDto> searchEventsByAdmin(List<Long> users, List<Event.EventState> states,
                                                  List<Long> categories, LocalDateTime rangeStart,
                                                  LocalDateTime rangeEnd, int from, int size) {

        if (rangeStart == null) {
            rangeStart = DEFAULT_START;
        }
        if (rangeEnd == null) {
            rangeEnd = DEFAULT_END;
        }

        Pageable pageable = PageRequest.of(from / size, size);

        List<Event> events = eventRepository.findEventsByAdmin(
                users, states, categories, rangeStart, rangeEnd, pageable);

        // Получаем просмотры для всех событий
        List<String> uris = events.stream()
                .map(e -> "/events/" + e.getId())
                .collect(Collectors.toList());

        Map<String, Long> viewsMap = statsService.getViewsMap(DEFAULT_START, DEFAULT_END, uris);

        return events.stream()
                .map(event -> {
                    Long views = viewsMap.getOrDefault("/events/" + event.getId(), 0L);
                    return EventMapper.toFullDto(event, views);
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public EventFullDto moderateEventByAdmin(Long eventId, UpdateEventAdminRequest dto) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Событие с id=" + eventId + " не найдено"));

        // Проверка на публикацию
        if ("PUBLISH_EVENT".equals(dto.getStateAction())) {
            if (event.getState() != Event.EventState.PENDING) {
                throw new ConflictException("Нельзя опубликовать событие в статусе " + event.getState());
            }
            if (event.getEventDate().isBefore(LocalDateTime.now().plusHours(1))) {
                throw new ConflictException("Нельзя опубликовать событие, которое начнется менее чем через час");
            }
        }

        // Проверка на отклонение
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
        log.info("Admin: изменено событие id={}, новый статус={}", eventId, event.getState());

        Long views = statsService.getViews("/events/" + eventId, DEFAULT_START, DEFAULT_END);
        return EventMapper.toFullDto(event, views);
    }

    //  МЕТОДЫ ДЛЯ ПОЛЬЗОВАТЕЛЕЙ

    @Override
    public List<EventShortDto> getUserEvents(Long userId, int from, int size) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("Пользователь с id=" + userId + " не найден");
        }

        Pageable pageable = PageRequest.of(from / size, size);
        List<Event> events = eventRepository.findAllByInitiatorId(userId, pageable).getContent();

        // Получаем просмотры
        List<String> uris = events.stream()
                .map(e -> "/events/" + e.getId())
                .collect(Collectors.toList());

        Map<String, Long> viewsMap = statsService.getViewsMap(DEFAULT_START, DEFAULT_END, uris);

        return events.stream()
                .map(event -> {
                    Long views = viewsMap.getOrDefault("/events/" + event.getId(), 0L);
                    return EventMapper.toShortDto(event, views);
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public EventFullDto createEvent(Long userId, NewEventDto dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь с id=" + userId + " не найден"));

        Category category = categoryRepository.findById(dto.getCategory())
                .orElseThrow(() -> new NotFoundException("Категория с id=" + dto.getCategory() + " не найдена"));

        // Проверка даты события
        if (dto.getEventDate().isBefore(LocalDateTime.now().plusHours(2))) {
            throw new BadRequestException("Дата события должна быть не ранее чем через 2 часа");
        }

        Event event = EventMapper.toEntity(dto, category, user);
        event = eventRepository.save(event);
        log.info("Создано событие id={} пользователем id={}", event.getId(), userId);

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

        Long views = statsService.getViews("/events/" + eventId, DEFAULT_START, DEFAULT_END);
        return EventMapper.toFullDto(event, views);
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
        // Проверка статуса
        if (event.getState() == Event.EventState.PUBLISHED) {
            throw new ConflictException("Нельзя изменить опубликованное событие");
        }
        // Проверка даты
        if (dto.getEventDate() != null) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime minEventDate = now.plusHours(2);

            if (dto.getEventDate().isBefore(minEventDate)) {
                throw new BadRequestException(
                        String.format("Дата события должна быть не ранее чем через 2 часа от текущего момента. " +
                                        "Текущее время: %s, минимальная дата: %s, полученная дата: %s",
                                now, minEventDate, dto.getEventDate())
                );
            }
        }
        // Обработка изменения статуса
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
        log.info("Пользователь id={} обновил событие id={}", userId, eventId);

        Long views = statsService.getViews("/events/" + eventId, DEFAULT_START, DEFAULT_END);
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

        return requestRepository.findAllByEventId(eventId)
                .stream()
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

        // Проверка лимита участников
        long confirmedRequests = event.getConfirmedRequests();
        long participantLimit = event.getParticipantLimit();

        if (participantLimit > 0 && confirmedRequests >= participantLimit) {
            throw new ConflictException("Достигнут лимит участников");
        }

        List<ParticipationRequest> requests = requestRepository.findAllById(dto.getRequestIds());

        // Проверка, что все заявки в статусе PENDING
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
                    // Оставшиеся заявки отклоняем
                    request.setStatus(ParticipationRequest.RequestStatus.REJECTED);
                    rejected.add(ParticipationRequestMapper.toDto(requestRepository.save(request)));
                } else {
                    request.setStatus(ParticipationRequest.RequestStatus.CONFIRMED);
                    confirmedRequests++;
                    confirmed.add(ParticipationRequestMapper.toDto(requestRepository.save(request)));
                }
            }

            // Обновляем количество подтвержденных заявок в событии
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