package ru.practicum.main.client;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableConfigurationProperties(MainStatsClientProperties.class)
@RequiredArgsConstructor
public class MainStatsClientAutoConfiguration {
    private final MainStatsClientProperties properties;

    @Bean
    @ConditionalOnMissingBean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    @ConditionalOnMissingBean
    public MainStatsClient mainStatsClient(RestTemplate restTemplate) {
        return new MainStatsClient(restTemplate, properties.getUrl());
    }
}