package ru.practicum.main.server.service;

import org.springframework.data.domain.Pageable;
import ru.practicum.main.server.dto.CommentDto;
import ru.practicum.main.server.dto.NewCommentDto;
import ru.practicum.main.server.dto.UpdateCommentRequest;

import java.util.List;

public interface CommentService {

    // пользователи
    CommentDto createComment(Long userId, Long eventId, NewCommentDto dto);

    CommentDto updateComment(Long userId, Long commentId, UpdateCommentRequest dto);

    void deleteCommentByUser(Long userId, Long commentId);

    List<CommentDto> getUserComments(Long userId, Pageable pageable);

    // паблик
    List<CommentDto> getEventComments(Long eventId, Pageable pageable);

    // админ
    List<CommentDto> searchComments(Long eventId, Long userId, String state, Pageable pageable);

    void deleteCommentByAdmin(Long commentId);
}