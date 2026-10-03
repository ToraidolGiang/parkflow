package com.parkflow.backend.plan;

import java.time.LocalTime;

public record GateAction(String gate, LocalTime openFrom, LocalTime normalOpen, LocalTime until) {
    public GateAction {
        if (gate == null || gate.isBlank() || openFrom == null || normalOpen == null
                || until == null || !openFrom.isBefore(until)) {
            throw new IllegalArgumentException("Invalid gate action window");
        }
    }
}