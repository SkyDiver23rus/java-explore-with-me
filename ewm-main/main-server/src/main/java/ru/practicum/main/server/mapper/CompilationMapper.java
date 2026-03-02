package ru.practicum.main.server.mapper;

import ru.practicum.main.dto.CompilationDto;
import ru.practicum.main.dto.NewCompilationDto;
import ru.practicum.main.dto.UpdateCompilationRequest;
import ru.practicum.main.server.model.Compilation;
import ru.practicum.main.server.model.Event;

import java.util.List;
import java.util.stream.Collectors;

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
        return CompilationDto.builder()
                .id(compilation.getId())
                .pinned(compilation.getPinned())
                .title(compilation.getTitle())
                .events(compilation.getEvents().stream()
                        .map(event -> {
                            Long viewCount = views != null && views.size() > compilation.getEvents().indexOf(event)
                                    ? views.get(compilation.getEvents().indexOf(event))
                                    : 0L;
                            return EventMapper.toShortDto(event, viewCount);
                        })
                        .collect(Collectors.toList()))
                .build();
    }
}