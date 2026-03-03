package ru.practicum.main.server.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.main.server.dto.ParticipationRequestDto;
import ru.practicum.main.server.exception.ConflictException;
import ru.practicum.main.server.exception.NotFoundException;
import ru.practicum.main.server.mapper.ParticipationRequestMapper;
import ru.practicum.main.server.model.*;
import ru.practicum.main.server.repository.EventRepository;
import ru.practicum.main.server.repository.ParticipationRequestRepository;
import ru.practicum.main.server.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RequestServiceImpl implements RequestService {
    private final ParticipationRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;

    @Override
    public List<ParticipationRequestDto> getUserRequests(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("Пользователь с id=" + userId + " не найден");
        }

        return requestRepository.findAllByRequesterId(userId)
                .stream()
                .map(ParticipationRequestMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ParticipationRequestDto createRequest(Long userId, Long eventId) {
        // Проверка существования пользователя
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь с id=" + userId + " не найден"));

        // Проверка существования события
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Событие с id=" + eventId + " не найдено"));

        // Инициатор события не может добавить запрос на участие в своём событии
        if (event.getInitiator().getId().equals(userId)) {
            throw new ConflictException("Инициатор события не может добавить запрос на участие в своём событии");
        }

        // Нельзя участвовать в неопубликованном событии
        if (event.getState() != Event.EventState.PUBLISHED) {
            throw new ConflictException("Нельзя участвовать в неопубликованном событии");
        }

        // Проверка на повторный запрос
        if (requestRepository.existsByEventIdAndRequesterId(eventId, userId)) {
            throw new ConflictException("Нельзя добавить повторный запрос на участие в событии");
        }

        // Проверка лимита участников
        long confirmedRequests = event.getConfirmedRequests();
        int participantLimit = event.getParticipantLimit();

        if (participantLimit > 0 && confirmedRequests >= participantLimit) {
            throw new ConflictException("Достигнут лимит участников");
        }

        // Создание запроса
        ParticipationRequest request = ParticipationRequest.builder()
                .created(LocalDateTime.now())
                .event(event)
                .requester(user)
                .status(ParticipationRequest.RequestStatus.PENDING)
                .build();

        // Если пре-модерация отключена, запрос сразу подтверждается
        if (!event.getRequestModeration() || participantLimit == 0) {
            request.setStatus(ParticipationRequest.RequestStatus.CONFIRMED);
            event.setConfirmedRequests(confirmedRequests + 1);
            eventRepository.save(event);
        }

        request = requestRepository.save(request);
        log.info("Создан запрос id={} на событие id={} от пользователя id={}",
                request.getId(), eventId, userId);

        return ParticipationRequestMapper.toDto(request);
    }

    @Override
    @Transactional
    public ParticipationRequestDto cancelRequest(Long userId, Long requestId) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("Пользователь с id=" + userId + " не найден");
        }

        ParticipationRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Запрос с id=" + requestId + " не найден"));

        if (!request.getRequester().getId().equals(userId)) {
            throw new NotFoundException("Запрос с id=" + requestId + " не принадлежит пользователю " + userId);
        }

        // Нельзя отменить уже подтвержденный запрос?
        if (request.getStatus() == ParticipationRequest.RequestStatus.CONFIRMED) {
            // Если нужно уменьшить счетчик подтвержденных запросов
            Event event = request.getEvent();
            event.setConfirmedRequests(event.getConfirmedRequests() - 1);
            eventRepository.save(event);
        }

        request.setStatus(ParticipationRequest.RequestStatus.CANCELED);
        request = requestRepository.save(request);
        log.info("Отменен запрос id={} пользователем id={}", requestId, userId);

        return ParticipationRequestMapper.toDto(request);
    }
}