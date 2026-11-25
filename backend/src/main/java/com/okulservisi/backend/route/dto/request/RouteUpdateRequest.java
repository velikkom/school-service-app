package com.okulservisi.backend.route.dto.request;

import com.okulservisi.backend.route.entity.RouteDirection;

/** Kısmi güncelleme (PATCH, ADMIN). */
public record RouteUpdateRequest(String name, RouteDirection direction, Boolean isActive) {
}
