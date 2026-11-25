package com.okulservisi.backend.route.dto.response;

import java.time.LocalTime;

public record StopResponse(Long id, Long routeId, String name, Integer sequenceNo, LocalTime plannedTime) {
}
