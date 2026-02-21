package ru.practicum.main.dto;

import lombok.*;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompilationDto {
    private List<EventShortDto> events;
    private Long id;
    private Boolean pinned;
    private String title;
}