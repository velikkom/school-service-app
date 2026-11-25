package com.okulservisi.backend.vehicle.dto.response;

public record VehicleResponse(Long id, String plate, Integer capacity, String model, boolean isActive) {
}
