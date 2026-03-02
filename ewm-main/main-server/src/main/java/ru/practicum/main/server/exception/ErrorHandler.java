package ru.practicum.main.server.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import ru.practicum.main.dto.ApiError;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class ErrorHandler {

    @ExceptionHandler
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError handleNotFound(final NotFoundException e) {
        log.error("404 Not Found: {}", e.getMessage());
        return build(HttpStatus.NOT_FOUND, e.getMessage(), "The required object was not found.",
                Collections.singletonList(e.getClass().getName()));
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError handleConflict(final ConflictException e) {
        log.error("409 Conflict: {}", e.getMessage());
        return build(HttpStatus.CONFLICT, e.getMessage(),
                "For the requested operation the conditions are not met.",
                Collections.singletonList(e.getClass().getName()));
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleBadRequest(final BadRequestException e) {
        log.error("400 Bad Request: {}", e.getMessage());
        return build(HttpStatus.BAD_REQUEST, e.getMessage(),
                "Incorrectly made request.",
                Collections.singletonList(e.getClass().getName()));
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleValidationException(final MethodArgumentNotValidException e) {
        log.error("400 Validation error: {}", e.getMessage());
        String message = e.getBindingResult().getAllErrors().stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .collect(Collectors.joining(", "));

        List<String> errors = e.getBindingResult().getAllErrors().stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .collect(Collectors.toList());

        return build(HttpStatus.BAD_REQUEST, message, "Incorrectly made request.", errors);
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleConstraintViolation(final ConstraintViolationException e) {
        log.error("400 Constraint violation: {}", e.getMessage());
        List<String> errors = e.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.toList());

        return build(HttpStatus.BAD_REQUEST, "Validation failed",
                "Incorrectly made request.", errors);
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleTypeMismatch(final MethodArgumentTypeMismatchException e) {
        log.error("400 Type mismatch: {}", e.getMessage());
        return build(HttpStatus.BAD_REQUEST,
                "Некорректное значение параметра: " + e.getName(),
                "Incorrectly made request.",
                Collections.singletonList(e.getMessage()));
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleNotReadable(final HttpMessageNotReadableException e) {
        log.error("400 Message not readable: {}", e.getMessage());
        return build(HttpStatus.BAD_REQUEST,
                "Некорректный формат тела запроса",
                "Incorrectly made request.",
                Collections.singletonList(e.getMostSpecificCause() != null
                        ? e.getMostSpecificCause().getMessage()
                        : e.getMessage()));
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleMissingParam(final MissingServletRequestParameterException e) {
        log.error("400 Missing request parameter: {}", e.getMessage());
        return build(HttpStatus.BAD_REQUEST,
                "Отсутствует обязательный параметр: " + e.getParameterName(),
                "Incorrectly made request.",
                Collections.singletonList(e.getMessage()));
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiError handleInternalError(final Throwable e) {
        log.error("500 Internal Server Error: {}", e.getMessage(), e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(),
                "Error occurred",
                Collections.singletonList(e.getClass().getName()));
    }

    private ApiError build(HttpStatus status, String message, String reason, List<String> errors) {
        return ApiError.builder()
                .errors(errors)
                .message(message)
                .reason(reason)
                .status(status.name())
                .timestamp(LocalDateTime.now())
                .build();
    }
}