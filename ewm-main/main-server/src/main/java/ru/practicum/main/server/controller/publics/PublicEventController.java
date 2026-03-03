package ru.practicum.main.server.controller.publics;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.validation.annotation.Validated;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.practicum.main.server.dto.EventFullDto;
import ru.practicum.main.server.dto.EventShortDto;
import ru.practicum.main.server.exception.NotFoundException;
import ru.practicum.main.server.service.EventService;
import ru.practicum.main.server.service.StatsService;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Slf4j
@Validated
@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
public class PublicEventController {
    private final EventService eventService;
    private final StatsService statsService;

    @GetMapping
    public ResponseEntity<List<EventShortDto>> getEvents(
            @RequestParam(required = false) String text,
            @RequestParam(required = false) List<Long> categories,
            @RequestParam(required = false) Boolean paid,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime rangeStart,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime rangeEnd,
            @RequestParam(defaultValue = "false") Boolean onlyAvailable,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") @PositiveOrZero int from,
            @RequestParam(defaultValue = "10") @Positive int size,
            HttpServletRequest request) {

        log.info("Public: поиск событий с from={}, size={}", from, size);

        try {
            statsService.saveHit(request.getRequestURI(), request.getRemoteAddr());
        } catch (Exception e) {
            log.warn("Не удалось сохранить hit в статистику: {}", e.getMessage());
        }

        List<EventShortDto> events = eventService.getPublishedEvents(
                text, categories, paid, rangeStart, rangeEnd,
                onlyAvailable, sort, from, size, request);

        log.info("Public: найдено событий: {}", events.size());
        return ResponseEntity.ok(events != null ? events : Collections.emptyList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventFullDto> getEvent(@PathVariable Long id, HttpServletRequest request) {
        log.info("Public: запрос события с id={}", id);
        try {
            EventFullDto event = eventService.getPublishedEventById(id, request);
            return ResponseEntity.ok(event);
        } catch (NotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Ошибка при получении события: {}", e.getMessage(), e);
            throw new NotFoundException("Событие с id=" + id + " не найдено");
        }
    }
}