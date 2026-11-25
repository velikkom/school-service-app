package com.okulservisi.backend.route.dto.response;

import com.okulservisi.backend.route.entity.RouteDirection;

public record RouteResponse(Long id, String name, RouteDirection direction, boolean isActive) {
}
