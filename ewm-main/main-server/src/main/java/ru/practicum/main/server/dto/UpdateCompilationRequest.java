package ru.practicum.main.server.dto;

import jakarta.validation.constraints.Size;
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

    @Size(max = 50, message = "Заголовок должен содержать не более 50 символов")
    private String title;
}