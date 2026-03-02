package ru.practicum.main.server.mapper;

import ru.practicum.main.dto.Location;
import ru.practicum.main.server.model.LocationEntity;

public class LocationMapper {

    public static LocationEntity toEntity(Location dto) {
        if (dto == null) {
            return null;
        }
        return LocationEntity.builder()
                .lat(dto.getLat())
                .lon(dto.getLon())
                .build();
    }

    public static Location toDto(LocationEntity entity) {
        if (entity == null) {
            return null;
        }
        return Location.builder()
                .lat(entity.getLat())
                .lon(entity.getLon())
                .build();
    }
}