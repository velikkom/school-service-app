package com.okulservisi.backend.route.dto.request;

import com.okulservisi.backend.route.entity.RouteDirection;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** docs/API.md "7. Güzergâh/Durak": {@code Route}: {@code name}, {@code direction} (MORNING/AFTERNOON). */
public record RouteCreateRequest(
        @NotBlank(message = "Ad zorunludur.") String name,
        @NotNull(message = "Yön zorunludur.") RouteDirection direction) {
}
