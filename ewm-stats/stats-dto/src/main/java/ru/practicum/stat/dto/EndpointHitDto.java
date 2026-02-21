package ru.practicum.stat.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EndpointHitDto {
    private Long id;
    private String app;
    private String uri;
    private String ip;
    private LocalDateTime timestamp;
}
