package ru.practicum.main.server.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.practicum.stat.client.StatsClient;
import ru.practicum.stat.dto.EndpointHitDto;
import ru.practicum.stat.dto.ViewStats;
import ru.practicum.main.server.mapper.StatsMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatsServiceImpl implements StatsService {
    private final StatsClient statsClient;

    @Value("${spring.application.name}")
    private String appName;

    @Override
    public void saveHit(String uri, String ip) {
        try {
            EndpointHitDto hit = EndpointHitDto.builder()
                    .app(appName)
                    .uri(uri)
                    .ip(ip)
                    .timestamp(LocalDateTime.now())
                    .build();
            statsClient.saveHit(hit);
            log.debug("Сохранен хит: uri={}, ip={}", uri, ip);
        } catch (Exception e) {
            log.error("Ошибка при сохранении статистики: {}", e.getMessage());
        }
    }

    @Override
    public Map<String, Long> getViewsMap(LocalDateTime start, LocalDateTime end, List<String> uris) {
        try {
            List<ViewStats> stats = statsClient.getStats(start, end, uris, false);
            return StatsMapper.toViewsMap(stats);
        } catch (Exception e) {
            log.error("Ошибка при получении статистики: {}", e.getMessage());
            return Map.of();
        }
    }

    @Override
    public Long getViews(String uri, LocalDateTime start, LocalDateTime end) {
        Map<String, Long> viewsMap = getViewsMap(start, end, List.of(uri));
        return viewsMap.getOrDefault(uri, 0L);
    }
}