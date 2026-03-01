package ru.practicum.stat.server.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Collections;

@Slf4j
@RestControllerAdvice
public class ErrorHandler {

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleBadRequest(final IllegalArgumentException e) {
        log.error("Bad request: {}", e.getMessage());
        return ApiError.builder()
                .errors(Collections.singletonList(e.getStackTrace()[0].toString()))
                .message(e.getMessage())
                .reason("Incorrectly made request.")
                .status(HttpStatus.BAD_REQUEST.name())
                .timestamp(LocalDateTime.now())
                .build();
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError handleNotFound(final RuntimeException e) {
        log.error("Not found: {}", e.getMessage());
        return ApiError.builder()
                .errors(Collections.singletonList(e.getStackTrace()[0].toString()))
                .message(e.getMessage())
                .reason("The required object was not found.")
                .status(HttpStatus.NOT_FOUND.name())
                .timestamp(LocalDateTime.now())
                .build();
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError handleConflict(final Exception e) {
        log.error("Conflict: {}", e.getMessage());
        return ApiError.builder()
                .errors(Collections.singletonList(e.getStackTrace()[0].toString()))
                .message(e.getMessage())
                .reason("Integrity constraint has been violated.")
                .status(HttpStatus.CONFLICT.name())
                .timestamp(LocalDateTime.now())
                .build();
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiError handleInternalError(final Throwable e) {
        log.error("Internal error: {}", e.getMessage());
        return ApiError.builder()
                .errors(Collections.singletonList(e.getStackTrace()[0].toString()))
                .message(e.getMessage())
                .reason("Error occurred")
                .status(HttpStatus.INTERNAL_SERVER_ERROR.name())
                .timestamp(LocalDateTime.now())
                .build();
    }
}