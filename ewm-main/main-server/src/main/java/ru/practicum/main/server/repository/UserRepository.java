package ru.practicum.main.server.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.practicum.main.server.model.User;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}