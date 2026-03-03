package ru.practicum.main.server.model;

public enum EventStateAction {
    // Для пользователя
    SEND_TO_REVIEW,
    CANCEL_REVIEW,

    // Для админа
    PUBLISH_EVENT,
    REJECT_EVENT
}