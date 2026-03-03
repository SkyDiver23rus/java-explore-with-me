package ru.practicum.main.server.mapper;

import ru.practicum.main.server.dto.*;
import ru.practicum.main.server.model.Category;
import ru.practicum.main.server.model.Event;
import ru.practicum.main.server.model.Event.EventState;
import ru.practicum.main.server.model.User;

import java.time.LocalDateTime;

public class EventMapper {

    public static Event toEntity(NewEventDto dto, Category category, User initiator) {
        return Event.builder()
                .annotation(dto.getAnnotation())
                .category(category)
                .description(dto.getDescription())
                .eventDate(dto.getEventDate())
                .initiator(initiator)
                .location(LocationMapper.toEntity(dto.getLocation()))
                .paid(dto.getPaid() != null ? dto.getPaid() : false)
                .participantLimit(dto.getParticipantLimit() != null ? dto.getParticipantLimit() : 0)
                .requestModeration(dto.getRequestModeration() != null ? dto.getRequestModeration() : true)
                .title(dto.getTitle())
                .state(EventState.PENDING)
                .confirmedRequests(0L)
                .views(0L)
                .createdOn(LocalDateTime.now())
                .build();
    }

    public static Event toEntity(UpdateEventUserRequest dto, Event existingEvent, Category category) {
        return Event.builder()
                .id(existingEvent.getId())
                .annotation(dto.getAnnotation() != null ? dto.getAnnotation() : existingEvent.getAnnotation())
                .category(category != null ? category : existingEvent.getCategory())
                .description(dto.getDescription() != null ? dto.getDescription() : existingEvent.getDescription())
                .eventDate(dto.getEventDate() != null ? dto.getEventDate() : existingEvent.getEventDate())
                .initiator(existingEvent.getInitiator())
                .location(dto.getLocation() != null ?
                        LocationMapper.toEntity(dto.getLocation()) : existingEvent.getLocation())
                .paid(dto.getPaid() != null ? dto.getPaid() : existingEvent.getPaid())
                .participantLimit(dto.getParticipantLimit() != null ?
                        dto.getParticipantLimit() : existingEvent.getParticipantLimit())
                .requestModeration(dto.getRequestModeration() != null ?
                        dto.getRequestModeration() : existingEvent.getRequestModeration())
                .title(dto.getTitle() != null ? dto.getTitle() : existingEvent.getTitle())
                .state(existingEvent.getState())
                .confirmedRequests(existingEvent.getConfirmedRequests())
                .views(existingEvent.getViews())
                .createdOn(existingEvent.getCreatedOn())
                .publishedOn(existingEvent.getPublishedOn())
                .build();
    }

    public static Event toEntity(UpdateEventAdminRequest dto, Event existingEvent, Category category) {
        EventState newState = existingEvent.getState();

        if (dto.getStateAction() != null) {
            switch (dto.getStateAction()) {
                case "PUBLISH_EVENT":
                    newState = EventState.PUBLISHED;
                    break;
                case "REJECT_EVENT":
                    newState = EventState.CANCELED;
                    break;
            }
        }

        return Event.builder()
                .id(existingEvent.getId())
                .annotation(dto.getAnnotation() != null ? dto.getAnnotation() : existingEvent.getAnnotation())
                .category(category != null ? category : existingEvent.getCategory())
                .description(dto.getDescription() != null ? dto.getDescription() : existingEvent.getDescription())
                .eventDate(dto.getEventDate() != null ? dto.getEventDate() : existingEvent.getEventDate())
                .initiator(existingEvent.getInitiator())
                .location(dto.getLocation() != null ?
                        LocationMapper.toEntity(dto.getLocation()) : existingEvent.getLocation())
                .paid(dto.getPaid() != null ? dto.getPaid() : existingEvent.getPaid())
                .participantLimit(dto.getParticipantLimit() != null ?
                        dto.getParticipantLimit() : existingEvent.getParticipantLimit())
                .requestModeration(dto.getRequestModeration() != null ?
                        dto.getRequestModeration() : existingEvent.getRequestModeration())
                .title(dto.getTitle() != null ? dto.getTitle() : existingEvent.getTitle())
                .state(newState)
                .confirmedRequests(existingEvent.getConfirmedRequests())
                .views(existingEvent.getViews())
                .createdOn(existingEvent.getCreatedOn())
                .publishedOn(newState == EventState.PUBLISHED ? LocalDateTime.now() : existingEvent.getPublishedOn())
                .build();
    }

    public static EventFullDto toFullDto(Event event, Long views) {
        if (event == null) return null;
        return EventFullDto.builder()
                .id(event.getId())
                .annotation(event.getAnnotation())
                .category(event.getCategory() != null ? CategoryMapper.toDto(event.getCategory()) : null)
                .confirmedRequests(event.getConfirmedRequests())
                .createdOn(event.getCreatedOn())
                .description(event.getDescription())
                .eventDate(event.getEventDate())
                .initiator(event.getInitiator() != null ? UserMapper.toShortDto(event.getInitiator()) : null)
                .location(event.getLocation() != null ? LocationMapper.toDto(event.getLocation()) : null)
                .paid(event.getPaid())
                .participantLimit(event.getParticipantLimit())
                .publishedOn(event.getPublishedOn())
                .requestModeration(event.getRequestModeration())
                .state(event.getState() != null ? event.getState().toString() : null)
                .title(event.getTitle())
                .views(views != null ? views : event.getViews())
                .build();
    }

    public static EventShortDto toShortDto(Event event, Long views) {
        return EventShortDto.builder()
                .id(event.getId())
                .annotation(event.getAnnotation())
                .category(CategoryMapper.toDto(event.getCategory()))
                .confirmedRequests(event.getConfirmedRequests())
                .eventDate(event.getEventDate())
                .initiator(UserMapper.toShortDto(event.getInitiator()))
                .paid(event.getPaid())
                .title(event.getTitle())
                .views(views != null ? views : event.getViews())
                .build();
    }
}