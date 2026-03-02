package ru.practicum.main.server.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.main.dto.CompilationDto;
import ru.practicum.main.dto.NewCompilationDto;
import ru.practicum.main.dto.UpdateCompilationRequest;
import ru.practicum.main.server.exception.BadRequestException;
import ru.practicum.main.server.exception.NotFoundException;
import ru.practicum.main.server.mapper.CompilationMapper;
import ru.practicum.main.server.model.Compilation;
import ru.practicum.main.server.model.Event;
import ru.practicum.main.server.repository.CompilationRepository;
import ru.practicum.main.server.repository.EventRepository;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompilationServiceImpl implements CompilationService {
    private final CompilationRepository compilationRepository;
    private final EventRepository eventRepository;
    private final StatsService statsService;

    @Override
    public List<CompilationDto> getCompilations(Boolean pinned, int from, int size) {
        Pageable pageable = PageRequest.of(from / size, size);

        List<Compilation> compilations;
        if (pinned == null) {
            compilations = compilationRepository.findAll(pageable).getContent();
        } else {
            compilations = compilationRepository.findAllByPinned(pinned, pageable).getContent();
        }

        List<String> allUris = compilations.stream()
                .flatMap(c -> c.getEvents().stream())
                .map(e -> "/events/" + e.getId())
                .distinct()
                .collect(Collectors.toList());

        Map<String, Long> viewsMap = allUris.isEmpty()
                ? Collections.emptyMap()
                : statsService.getViewsMap(LocalDateTime.now().minusYears(100), LocalDateTime.now(), allUris);

        return compilations.stream()
                .map(compilation -> {
                    List<Long> views = compilation.getEvents().stream()
                            .map(e -> viewsMap.getOrDefault("/events/" + e.getId(), 0L))
                            .collect(Collectors.toList());
                    return CompilationMapper.toDto(compilation, views);
                })
                .collect(Collectors.toList());
    }

    @Override
    public CompilationDto getCompilationById(Long compId) {
        Compilation compilation = compilationRepository.findById(compId)
                .orElseThrow(() -> new NotFoundException("Подборка с id=" + compId + " не найдена"));

        List<String> uris = compilation.getEvents().stream()
                .map(e -> "/events/" + e.getId())
                .collect(Collectors.toList());

        Map<String, Long> viewsMap = statsService.getViewsMap(
                LocalDateTime.now().minusYears(100), LocalDateTime.now(), uris);

        List<Long> views = compilation.getEvents().stream()
                .map(e -> viewsMap.getOrDefault("/events/" + e.getId(), 0L))
                .collect(Collectors.toList());

        return CompilationMapper.toDto(compilation, views);
    }

    @Override
    @Transactional
    public CompilationDto createCompilation(NewCompilationDto dto) {
        List<Event> events = dto.getEvents() == null
                ? Collections.emptyList()
                : eventRepository.findAllById(dto.getEvents());

        Compilation compilation = CompilationMapper.toEntity(dto, events);
        compilation = compilationRepository.save(compilation);
        log.info("Создана подборка: id={}, title={}", compilation.getId(), compilation.getTitle());

        return getCompilationById(compilation.getId());
    }

    @Override
    @Transactional
    public CompilationDto updateCompilation(Long compId, UpdateCompilationRequest dto) {
        Compilation compilation = compilationRepository.findById(compId)
                .orElseThrow(() -> new NotFoundException("Подборка с id=" + compId + " не найдена"));
        if (dto.getTitle() != null && dto.getTitle().length() > 50) {
            throw new BadRequestException("Заголовок не может быть длиннее 50 символов");
        }
        List<Event> events = dto.getEvents() == null
                ? compilation.getEvents()
                : eventRepository.findAllById(dto.getEvents());

        compilation = CompilationMapper.toEntity(dto, compilation, events);
        compilation = compilationRepository.save(compilation);
        log.info("Обновлена подборка с id={}", compId);

        return getCompilationById(compId);
    }

    @Override
    @Transactional
    public void deleteCompilation(Long compId) {
        if (!compilationRepository.existsById(compId)) {
            throw new NotFoundException("Подборка с id=" + compId + " не найдена");
        }
        compilationRepository.deleteById(compId);
        log.info("Удалена подборка с id={}", compId);
    }
}