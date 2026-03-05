package ru.practicum.main.server.mapper;

import ru.practicum.main.server.dto.NewUserRequest;
import ru.practicum.main.server.dto.UserDto;
import ru.practicum.main.server.dto.UserShortDto;
import ru.practicum.main.server.model.User;

public class UserMapper {

    public static User toEntity(NewUserRequest request) {
        return User.builder()
                .email(request.getEmail())
                .name(request.getName())
                .build();
    }

    public static UserDto toDto(User user) {
        return UserDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .build();
    }

    public static UserShortDto toShortDto(User user) {
        if (user == null) {
            return null;
        }
        return UserShortDto.builder()
                .id(user.getId())
                .name(user.getName())
                .build();
    }
}