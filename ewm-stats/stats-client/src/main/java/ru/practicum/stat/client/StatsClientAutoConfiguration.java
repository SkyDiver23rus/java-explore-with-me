package ru.practicum.stat.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class StatsClientAutoConfiguration {

    @Value("${stats.server.url:http://localhost:9090}")
    private String statsServerUrl;

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public StatsClient statsClient() {
        return new StatsClient(restTemplate(), statsServerUrl);
    }
}