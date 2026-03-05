package ru.practicum.main.server.dto;

import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateCommentRequest {

    @Size(min = 1, max = 350, message = "Комментарий должен содержать от 1 до 350 символов")
    private String content;
}