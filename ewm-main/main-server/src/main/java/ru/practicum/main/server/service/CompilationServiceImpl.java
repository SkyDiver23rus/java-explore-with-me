package ru.practicum.main.server.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.main.server.dto.CompilationDto;
import ru.practicum.main.server.dto.NewCompilationDto;
import ru.practicum.main.server.dto.UpdateCompilationRequest;
import ru.practicum.main.server.exception.BadRequestException;
import ru.practicum.main.server.exception.NotFoundException;
import ru.practicum.main.server.mapper.CompilationMapper;
import ru.practicum.main.server.model.Compilation;
import ru.practicum.main.server.model.Event;
import ru.practicum.main.server.model.ParticipationRequest;
import ru.practicum.main.server.repository.CompilationRepository;
import ru.practicum.main.server.repository.EventRepository;
import ru.practicum.main.server.repository.ParticipationRequestRepository;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
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
    private final ParticipationRequestRepository requestRepository; // Добавлено!

    @Override
    public List<CompilationDto> getCompilations(Boolean pinned, int from, int size) {
        Pageable pageable = PageRequest.of(from / size, size);

        List<Compilation> compilations;
        if (pinned == null) {
            compilations = compilationRepository.findAll(pageable).getContent();
        } else {
            compilations = compilationRepository.findAllByPinned(pinned, pageable).getContent();
        }

        if (compilations.isEmpty()) {
            return Collections.emptyList();
        }

        return compilations.stream()
                .map(this::mapToDtoWithViews)
                .collect(Collectors.toList());
    }

    @Override
    public CompilationDto getCompilationById(Long compId) {
        Compilation compilation = compilationRepository.findById(compId)
                .orElseThrow(() -> new NotFoundException("Подборка с id=" + compId + " не найдена"));

        return mapToDtoWithViews(compilation);
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

        return mapToDtoWithViews(compilation);
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

        return mapToDtoWithViews(compilation);
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

    private CompilationDto mapToDtoWithViews(Compilation compilation) {
        List<Event> events = compilation.getEvents();

        if (events.isEmpty()) {
            return CompilationMapper.toDto(compilation, Collections.emptyList(), Collections.emptyList());
        }

        // Собираем все uri событий
        List<String> uris = events.stream()
                .map(e -> "/events/" + e.getId())
                .collect(Collectors.toList());

        // Вычисляем минимальную дату создания события
        LocalDateTime earliestEventDate = events.stream()
                .map(Event::getCreatedOn)
                .min(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now().minusYears(1));

        // Запрашиваем просмотры
        Map<String, Long> viewsMap = statsService.getViewsMap(
                earliestEventDate,
                LocalDateTime.now(),
                uris
        );

        // Получаем подтверждённые запросы для всех событий одним запросом
        List<Long> eventIds = events.stream()
                .map(Event::getId)
                .collect(Collectors.toList());

        Map<Long, Long> confirmedMap = new HashMap<>();
        try {
            List<ParticipationRequestRepository.EventConfirmedCount> counts =
                    requestRepository.countConfirmedRequestsByEventIds(
                            eventIds, ParticipationRequest.RequestStatus.CONFIRMED);

            for (ParticipationRequestRepository.EventConfirmedCount c : counts) {
                confirmedMap.put(c.getEventId(), c.getConfirmedRequests());
            }
        } catch (Exception e) {
            log.error("Ошибка получения confirmedRequests: {}", e.getMessage());
        }

        // Собираем просмотры для каждого события
        List<Long> views = events.stream()
                .map(e -> viewsMap.getOrDefault("/events/" + e.getId(), 0L))
                .collect(Collectors.toList());

        // Собираем confirmedRequests для каждого события
        List<Long> confirmedRequests = events.stream()
                .map(e -> confirmedMap.getOrDefault(e.getId(), 0L))
                .collect(Collectors.toList());

        return CompilationMapper.toDto(compilation, views, confirmedRequests);
    }
}