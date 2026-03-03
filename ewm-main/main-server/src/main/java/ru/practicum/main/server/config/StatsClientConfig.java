package ru.practicum.main.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import ru.practicum.stat.client.StatsClient;

@Configuration
public class StatsClientConfig {

    @Value("${stats.client.url:http://localhost:9090}")
    private String statsServerUrl;

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public StatsClient statsClient(RestTemplate restTemplate) {
        return new StatsClient(restTemplate, statsServerUrl);
    }
}