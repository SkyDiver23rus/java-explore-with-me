package ru.practicum.main.server.mapper;

import ru.practicum.main.server.dto.CommentDto;
import ru.practicum.main.server.model.Comment;

public class CommentMapper {

    public static CommentDto toDto(Comment comment) {
        if (comment == null) return null;

        return CommentDto.builder()
                .id(comment.getId())
                .content(comment.getContent())
                .author(UserMapper.toShortDto(comment.getAuthor()))
                .eventId(comment.getEvent().getId())
                .createdOn(comment.getCreatedOn())
                .updatedOn(comment.getUpdatedOn())
                .state(comment.getState().toString())
                .build();
    }
}