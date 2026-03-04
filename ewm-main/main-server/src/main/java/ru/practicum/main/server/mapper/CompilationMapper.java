package ru.practicum.main.server.mapper;

import ru.practicum.main.server.dto.CompilationDto;
import ru.practicum.main.server.dto.NewCompilationDto;
import ru.practicum.main.server.dto.UpdateCompilationRequest;
import ru.practicum.main.server.model.Compilation;
import ru.practicum.main.server.model.Event;
import ru.practicum.main.server.model.ParticipationRequest;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class CompilationMapper {

    public static Compilation toEntity(NewCompilationDto dto, List<Event> events) {
        return Compilation.builder()
                .pinned(dto.getPinned() != null ? dto.getPinned() : false)
                .title(dto.getTitle())
                .events(events)
                .build();
    }

    public static Compilation toEntity(UpdateCompilationRequest dto, Compilation existing, List<Event> events) {
        return Compilation.builder()
                .id(existing.getId())
                .pinned(dto.getPinned() != null ? dto.getPinned() : existing.getPinned())
                .title(dto.getTitle() != null ? dto.getTitle() : existing.getTitle())
                .events(events != null ? events : existing.getEvents())
                .build();
    }

    public static CompilationDto toDto(Compilation compilation, List<Long> views) {
        List<Event> events = compilation.getEvents();

        return CompilationDto.builder()
                .id(compilation.getId())
                .pinned(compilation.getPinned())
                .title(compilation.getTitle())
                .events(IntStream.range(0, events.size())
                        .mapToObj(i -> {
                            Event event = events.get(i);
                            long viewCount = (views != null && i < views.size()) ? views.get(i) : 0L;

                            long confirmed = event.getRequests() == null ? 0L :
                                    event.getRequests().stream()
                                            .filter(r -> r.getStatus() == ParticipationRequest.RequestStatus.CONFIRMED)
                                            .count();

                            return EventMapper.toShortDto(event, confirmed, viewCount);
                        })
                        .collect(Collectors.toList()))
                .build();
    }
}