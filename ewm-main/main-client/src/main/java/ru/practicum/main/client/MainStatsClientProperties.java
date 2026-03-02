package ru.practicum.main.client;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "stats.client")
public class MainStatsClientProperties {

    @NotBlank(message = "URL сервера статистики не может быть пустым")
    private String url;
}