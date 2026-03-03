package ru.practicum.main.server.service;

import ru.practicum.main.server.dto.NewUserRequest;
import ru.practicum.main.server.dto.UserDto;

import java.util.List;

public interface UserService {
    List<UserDto> getUsers(List<Long> ids, int from, int size);

    UserDto createUser(NewUserRequest dto);

    void deleteUser(Long userId);
}