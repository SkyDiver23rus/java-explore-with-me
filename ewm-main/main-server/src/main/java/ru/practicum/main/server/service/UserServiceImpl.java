package ru.practicum.main.server.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.main.server.dto.NewUserRequest;
import ru.practicum.main.server.dto.UserDto;
import ru.practicum.main.server.exception.ConflictException;
import ru.practicum.main.server.exception.NotFoundException;
import ru.practicum.main.server.mapper.UserMapper;
import ru.practicum.main.server.model.User;
import ru.practicum.main.server.repository.UserRepository;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;

    @Override
    public List<UserDto> getUsers(List<Long> ids, int from, int size) {
        Pageable pageable = PageRequest.of(from / size, size);

        if (ids == null || ids.isEmpty()) {
            return userRepository.findAll(pageable)
                    .stream()
                    .map(UserMapper::toDto)
                    .collect(Collectors.toList());
        } else {
            return userRepository.findAllById(ids)
                    .stream()
                    .map(UserMapper::toDto)
                    .collect(Collectors.toList());
        }
    }

    @Override
    @Transactional
    public UserDto createUser(NewUserRequest dto) {
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new ConflictException("Пользователь с email '" + dto.getEmail() + "' уже существует");
        }

        User user = UserMapper.toEntity(dto);
        user = userRepository.save(user);
        log.info("Создан пользователь: id={}, email={}, name={}",
                user.getId(), user.getEmail(), user.getName());
        return UserMapper.toDto(user);
    }

    @Override
    @Transactional
    public void deleteUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("Пользователь с id=" + userId + " не найден");
        }
        userRepository.deleteById(userId);
        log.info("Удален пользователь с id={}", userId);
    }
}