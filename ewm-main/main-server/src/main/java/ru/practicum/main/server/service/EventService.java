package ru.practicum.main.server.service;

import jakarta.servlet.http.HttpServletRequest;
import ru.practicum.main.server.dto.*;
import ru.practicum.main.server.model.Event;

import java.time.LocalDateTime;
import java.util.List;

public interface EventService {
    // Публичные методы
    List<EventShortDto> getPublishedEvents(String text, List<Long> categories, Boolean paid,
                                           LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                           Boolean onlyAvailable, String sort,
                                           int from, int size, HttpServletRequest request);

    EventFullDto getPublishedEventById(Long id, HttpServletRequest request);

    // Методы для админа
    List<EventFullDto> searchEventsByAdmin(List<Long> users, List<Event.EventState> states,
                                           List<Long> categories, LocalDateTime rangeStart,
                                           LocalDateTime rangeEnd, int from, int size);

    EventFullDto moderateEventByAdmin(Long eventId, UpdateEventAdminRequest dto);

    // Методы для пользователей
    List<EventShortDto> getUserEvents(Long userId, int from, int size);

    EventFullDto createEvent(Long userId, NewEventDto dto);

    EventFullDto getUserEventById(Long userId, Long eventId);

    EventFullDto updateEventByUser(Long userId, Long eventId, UpdateEventUserRequest dto);

    List<ParticipationRequestDto> getEventRequests(Long userId, Long eventId);

    EventRequestStatusUpdateResult updateRequestStatus(Long userId, Long eventId,
                                                       EventRequestStatusUpdateRequest dto);
}