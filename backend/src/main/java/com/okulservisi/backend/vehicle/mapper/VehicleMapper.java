package com.okulservisi.backend.vehicle.mapper;

import com.okulservisi.backend.vehicle.dto.response.VehicleResponse;
import com.okulservisi.backend.vehicle.dto.request.VehicleUpdateRequest;
import com.okulservisi.backend.vehicle.entity.Vehicle;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface VehicleMapper {

    @Mapping(target = "isActive", source = "active")
    VehicleResponse toResponse(Vehicle vehicle);

    /**
     * Kısmi güncelleme: null alanlar dokunulmadan bırakılır. {@code plate} KASITLI OLARAK hariç
     * tutulur — normalize etme (büyük harf/boşluksuz) ve unique kontrolü domain'e özgü mantıktır,
     * VehicleService.update() içinde elle (mapper çağrısından ÖNCE) yapılır.
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "plate", ignore = true)
    @Mapping(target = "active", source = "isActive")
    void updateFromRequest(@MappingTarget Vehicle vehicle, VehicleUpdateRequest request);
}
