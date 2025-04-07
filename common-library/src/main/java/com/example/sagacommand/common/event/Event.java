package com.example.sagacommand.common.event;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;


@Getter
public abstract class Event {
    private final UUID eventId;
    private final LocalDateTime timestamp;

    protected Event() {
        this.eventId = UUID.randomUUID();
        this.timestamp = LocalDateTime.now();
    }
}
