package ru.practicum.main.server.mapper;

import ru.practicum.stat.dto.ViewStats;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class StatsMapper {

    public static Long extractViewsByUri(String uri, List<ViewStats> stats) {
        return stats.stream()
                .filter(stat -> stat.getUri().equals(uri))
                .map(ViewStats::getHits)
                .findFirst()
                .orElse(0L);
    }

    public static Map<String, Long> toViewsMap(List<ViewStats> stats) {
        return stats.stream()
                .collect(Collectors.toMap(
                        ViewStats::getUri,
                        ViewStats::getHits,
                        (v1, v2) -> v1));
    }
}