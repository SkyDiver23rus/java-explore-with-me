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

            log.info("Сохраняем хит: {}", hit);
            statsClient.saveHit(hit);
            log.debug("Хит сохранён");
        } catch (Exception e) {
            log.error("Ошибка при сохранении статистики", e);
        }
    }

    @Override
    public Map<String, Long> getViewsMap(LocalDateTime start, LocalDateTime end, List<String> uris) {
        try {
            log.info("Запрашиваем статистику за период {} - {}, uris: {}", start, end, uris);
            List<ViewStats> stats = statsClient.getStats(start, end, uris, true);
            log.info("Получено записей: {}", stats.size());
            return StatsMapper.toViewsMap(stats);
        } catch (Exception e) {
            log.error("Ошибка при получении статистики", e);
            return Map.of();
        }
    }

    @Override
    public Long getViews(String uri, LocalDateTime start, LocalDateTime end) {
        Map<String, Long> viewsMap = getViewsMap(start, end, List.of(uri));
        return viewsMap.getOrDefault(uri, 0L);
    }
}