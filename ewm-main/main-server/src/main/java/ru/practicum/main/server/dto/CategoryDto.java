package ru.practicum.main.server.dto;

import lombok.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryDto {

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long id;

    @NotBlank(message = "Название категории не может быть пустым")
    @Size(min = 1, max = 50, message = "Название категории должно содержать от 1 до 50 символов")
    private String name;
}