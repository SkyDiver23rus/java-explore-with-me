package ru.practicum.main.server.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.main.server.dto.CommentDto;
import ru.practicum.main.server.dto.NewCommentDto;
import ru.practicum.main.server.dto.UpdateCommentRequest;
import ru.practicum.main.server.exception.BadRequestException;
import ru.practicum.main.server.exception.ConflictException;
import ru.practicum.main.server.exception.NotFoundException;
import ru.practicum.main.server.mapper.CommentMapper;
import ru.practicum.main.server.model.*;
import ru.practicum.main.server.repository.CommentRepository;
import ru.practicum.main.server.repository.EventRepository;
import ru.practicum.main.server.repository.UserRepository;
import ru.practicum.main.server.service.CommentService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;

    @Override
    @Transactional
    public CommentDto createComment(Long userId, Long eventId, NewCommentDto dto) {
        log.info("Создание комментария пользователем {} к событию {}", userId, eventId);

        User author = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь с id=" + userId + " не найден"));

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Событие с id=" + eventId + " не найдено"));

        // Проверяем публикацию
        if (event.getState() != Event.EventState.PUBLISHED) {
            throw new BadRequestException("Нельзя комментировать неопубликованное событие");
        }

        Comment comment = Comment.builder()
                .text(dto.getText())
                .author(author)
                .event(event)
                .createdOn(LocalDateTime.now())
                .state(CommentState.PUBLISHED)
                .build();

        comment = commentRepository.save(comment);
        log.info("Создан комментарий с id={}", comment.getId());

        return CommentMapper.toDto(comment);
    }

    @Override
    @Transactional
    public CommentDto updateComment(Long userId, Long commentId, UpdateCommentRequest dto) {
        log.info("Обновление комментария {} пользователем {}", commentId, userId);

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("Комментарий с id=" + commentId + " не найден"));

        if (!comment.getAuthor().getId().equals(userId)) {
            throw new NotFoundException("Комментарий с id=" + commentId + " не принадлежит пользователю " + userId);
        }

        if (comment.getState() == CommentState.DELETED) {
            throw new ConflictException("Нельзя редактировать удалённый комментарий");
        }

        comment.setText(dto.getText());
        comment.setUpdatedOn(LocalDateTime.now());

        comment = commentRepository.save(comment);
        log.info("Комментарий {} обновлён", commentId);

        return CommentMapper.toDto(comment);
    }

    @Override
    @Transactional
    public void deleteCommentByUser(Long userId, Long commentId) {
        log.info("Удаление комментария {} пользователем {}", commentId, userId);

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("Комментарий с id=" + commentId + " не найден"));

        if (!comment.getAuthor().getId().equals(userId)) {
            throw new NotFoundException("Комментарий с id=" + commentId + " не принадлежит пользователю " + userId);
        }

        //  удаление
        comment.setState(CommentState.DELETED);
        commentRepository.save(comment);

        log.info("Комментарий {} помечен как удалённый", commentId);
    }

    @Override
    public List<CommentDto> getUserComments(Long userId, Pageable pageable) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("Пользователь с id=" + userId + " не найден");
        }

        return commentRepository.findAllByAuthorId(userId, pageable)
                .stream()
                .map(CommentMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<CommentDto> getEventComments(Long eventId, Pageable pageable) {
        if (!eventRepository.existsById(eventId)) {
            throw new NotFoundException("Событие с id=" + eventId + " не найдено");
        }

        //  только опубликованные комментарии
        return commentRepository.findAllByEventIdAndState(eventId, CommentState.PUBLISHED, pageable)
                .stream()
                .map(CommentMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<CommentDto> searchComments(Long eventId, Long userId, String state, Pageable pageable) {
        // Для админа — все комментарии, включая удалённые
        if (eventId != null) {
            return commentRepository.findAllByEventId(eventId, pageable)
                    .stream()
                    .map(CommentMapper::toDto)
                    .collect(Collectors.toList());
        } else if (userId != null) {
            return commentRepository.findAllByAuthorId(userId, pageable)
                    .stream()
                    .map(CommentMapper::toDto)
                    .collect(Collectors.toList());
        } else {
            return commentRepository.findAll(pageable)
                    .stream()
                    .map(CommentMapper::toDto)
                    .collect(Collectors.toList());
        }
    }

    @Override
    @Transactional
    public void deleteCommentByAdmin(Long commentId) {
        log.info("Админ удаляет комментарий {}", commentId);

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("Комментарий с id=" + commentId + " не найден"));

        // удаление для админа
        commentRepository.delete(comment);

        log.info("Комментарий {} удалён администратором", commentId);
    }
}