package ru.practicum.main.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import ru.practicum.main.client.MainStatsClientAutoConfiguration;

@SpringBootApplication
@Import(MainStatsClientAutoConfiguration.class)
public class MainServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MainServiceApplication.class, args);
    }
}