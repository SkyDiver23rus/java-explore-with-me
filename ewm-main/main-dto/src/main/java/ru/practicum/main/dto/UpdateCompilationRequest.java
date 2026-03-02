package ru.practicum.main.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateCompilationRequest {
    private List<Long> events;
    private Boolean pinned;
    private String title;
}