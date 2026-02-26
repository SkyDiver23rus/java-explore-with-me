package ru.practicum.stat.server.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.stat.dto.EndpointHitDto;
import ru.practicum.stat.dto.ViewStats;
import ru.practicum.stat.server.model.EndpointHit;
import ru.practicum.stat.server.repository.EndpointHitRepository;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatsService {
    private final EndpointHitRepository repository;

    @Transactional
    public EndpointHitDto saveHit(EndpointHitDto dto) {
        log.info("Сохранение статистики: app={}, uri={}, ip={}, timestamp={}",
                dto.getApp(), dto.getUri(), dto.getIp(), dto.getTimestamp());

        EndpointHit hit = EndpointHit.builder()
                .app(dto.getApp())
                .uri(dto.getUri())
                .ip(dto.getIp())
                .createTs(dto.getTimestamp())
                .build();

        EndpointHit savedHit = repository.save(hit);
        log.debug("Сохранена запись с id: {}", savedHit.getId());

        return dto;
    }

    @Transactional(readOnly = true)
    public List<ViewStats> getStats(LocalDateTime start, LocalDateTime end, List<String> uris, boolean unique) {
        log.info("Получение статистики за период с {} по {}, uris={}, unique={}",
                start, end, uris, unique);

        // Проверка корректности дат
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("Дата начала не может быть позже даты окончания");
        }

        List<ViewStats> stats;

        // Обработка случая, когда uris пустой или null
        if (uris == null || uris.isEmpty()) {
            if (unique) {
                stats = repository.findUniqueStatsWithoutUris(start, end);
            } else {
                stats = repository.findStatsWithoutUris(start, end);
            }
        } else {
            if (unique) {
                stats = repository.findUniqueStatsWithUris(start, end, uris);
            } else {
                stats = repository.findStatsWithUris(start, end, uris);
            }
        }

        log.debug("Найдено {} записей статистики", stats.size());
        return stats;
    }

    @Transactional(readOnly = true)
    public List<ViewStats> getStats(LocalDateTime start, LocalDateTime end, List<String> uris) {
        return getStats(start, end, uris, false);
    }

    @Transactional(readOnly = true)
    public List<ViewStats> getStats(LocalDateTime start, LocalDateTime end) {
        return getStats(start, end, null, false);
    }
}