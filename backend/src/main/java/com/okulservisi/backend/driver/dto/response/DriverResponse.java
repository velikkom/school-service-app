package com.okulservisi.backend.driver.dto.response;

public record DriverResponse(Long id, Long userId, String licenseNo, boolean isActive) {
}
