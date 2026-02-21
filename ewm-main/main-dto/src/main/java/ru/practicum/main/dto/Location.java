package ru.practicum.main.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Location {
    private Float lat;
    private Float lon;
}