package ru.practicum.main.server.dto;

import lombok.*;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParticipationRequestDto {
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime created;
    private Long event;
    private Long id;
    private Long requester;
    private String status;
}