package com.okulservisi.backend.route.dto.response;

import java.time.LocalTime;

/** docs/API.md §25 {@code GET /api/stop-change-requests/options} satırı. */
public record StopOptionResponse(
        Long stopId,
        String stopName,
        LocalTime plannedTime,
        Integer sequenceNo,
        Long routeId,
        String routeName) {
}
