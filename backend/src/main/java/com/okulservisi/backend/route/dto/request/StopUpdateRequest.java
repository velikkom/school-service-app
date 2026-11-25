package com.okulservisi.backend.route.dto.request;

import jakarta.validation.constraints.Positive;
import java.time.LocalTime;

public record StopUpdateRequest(
        String name,
        @Positive(message = "sequenceNo pozitif olmalıdır.") Integer sequenceNo,
        LocalTime plannedTime) {
}
