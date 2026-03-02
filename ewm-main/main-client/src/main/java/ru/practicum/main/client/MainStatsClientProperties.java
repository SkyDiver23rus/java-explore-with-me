package ru.practicum.main.client;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotBlank;

@Data
@Validated
@ConfigurationProperties(prefix = "stats.client")
public class MainStatsClientProperties {

    @NotBlank(message = "URL сервера статистики не может быть пустым")
    private String url = "http://localhost:9090";
}