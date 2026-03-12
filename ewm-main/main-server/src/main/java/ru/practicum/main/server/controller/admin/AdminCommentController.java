package ru.practicum.main.server.controller.admin;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.practicum.main.server.dto.CommentDto;
import ru.practicum.main.server.service.CommentService;

import java.util.List;

@Slf4j
@Validated
@RestController
@RequestMapping("/admin/comments")
@RequiredArgsConstructor
public class AdminCommentController {

    private final CommentService commentService;

    @DeleteMapping("/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@PathVariable Long commentId) {
        log.info("Admin: удаление комментария {}", commentId);
        commentService.deleteCommentByAdmin(commentId);
    }

    @GetMapping
    public List<CommentDto> searchComments(
            @RequestParam(required = false) Long eventId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String state,
            @RequestParam(defaultValue = "0") @PositiveOrZero int from,
            @RequestParam(defaultValue = "10") @Positive int size) {
        log.info("Admin: поиск комментариев eventId={}, userId={}, state={}", eventId, userId, state);
        Pageable pageable = PageRequest.of(from / size, size);
        return commentService.searchComments(eventId, userId, state, pageable);
    }
}