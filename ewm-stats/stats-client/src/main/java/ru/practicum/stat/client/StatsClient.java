package ru.practicum.stat.client;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import ru.practicum.stat.dto.EndpointHitDto;
import ru.practicum.stat.dto.ViewStats;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class StatsClient {
    private final RestTemplate restTemplate;
    private final String serverUrl;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public StatsClient() {
        this.restTemplate = new RestTemplate();
        this.serverUrl = "http://localhost:9090";
    }

    public StatsClient(String serverUrl) {
        this.restTemplate = new RestTemplate();
        this.serverUrl = serverUrl;
    }

    public StatsClient(RestTemplate restTemplate, String serverUrl) {
        this.restTemplate = restTemplate;
        this.serverUrl = serverUrl;
    }

    public EndpointHitDto saveHit(EndpointHitDto hitDto) {
        String url = serverUrl + "/hit";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<EndpointHitDto> request = new HttpEntity<>(hitDto, headers);
        ResponseEntity<EndpointHitDto> response = restTemplate.exchange(
                url, HttpMethod.POST, request, EndpointHitDto.class
        );

        return response.getBody();
    }

    public List<ViewStats> getStats(LocalDateTime start, LocalDateTime end,
                                    List<String> uris, Boolean unique) {

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(serverUrl + "/stats")
                .queryParam("start", start.format(FORMATTER))
                .queryParam("end", end.format(FORMATTER))
                .queryParam("unique", unique);

        if (uris != null && !uris.isEmpty()) {
            builder.queryParam("uris", String.join(",", uris));
        }

        String url = builder.build().toUriString();

        ResponseEntity<List<ViewStats>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<ViewStats>>() {}
        );

        return response.getBody();
    }

    public List<ViewStats> getStats(LocalDateTime start, LocalDateTime end, List<String> uris) {
        return getStats(start, end, uris, false);
    }

    public List<ViewStats> getStats(LocalDateTime start, LocalDateTime end) {
        return getStats(start, end, null, false);
    }
}