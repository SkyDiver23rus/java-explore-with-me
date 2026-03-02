package ru.practicum.main.server.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface StatsService {
    void saveHit(String uri, String ip);

    Map<String, Long> getViewsMap(LocalDateTime start, LocalDateTime end, List<String> uris);

    Long getViews(String uri, LocalDateTime start, LocalDateTime end);
}