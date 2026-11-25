package com.okulservisi.backend.trip.mapper;

import com.okulservisi.backend.trip.dto.response.TripResponse;
import com.okulservisi.backend.trip.entity.Trip;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * NOT (2026-11-18): görüntüleme alanları ilişkili entity'lerin alanlarına eriştiği için {@code Trip}
 * nesnesi ilişkileri YÜKLENMİŞ olmalıdır ({@code TripRepository} entity graph'ları veya
 * {@code TripServiceImpl#initializeView}) — {@code open-in-view: false}.
 */
@Mapper(componentModel = "spring")
public interface TripMapper {

    @Mapping(target = "routeId", expression = "java(trip.getRoute().getId())")
    @Mapping(target = "routeName", expression = "java(trip.getRoute().getName())")
    @Mapping(target = "vehicleId", expression = "java(trip.getVehicle().getId())")
    @Mapping(target = "vehiclePlate", expression = "java(trip.getVehicle().getPlate())")
    @Mapping(target = "driverId", expression = "java(trip.getDriver().getId())")
    @Mapping(target = "driverFullName", expression = "java(trip.getDriver().getUser().getFullName())")
    @Mapping(target = "hostessId", expression = "java(trip.getHostess() == null ? null : trip.getHostess().getId())")
    @Mapping(target = "hostessFullName",
            expression = "java(trip.getHostess() == null ? null : trip.getHostess().getUser().getFullName())")
    @Mapping(target = "openCancellationRequestId", ignore = true)
    TripResponse toResponse(Trip trip);
}
