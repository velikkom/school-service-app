package com.okulservisi.backend.driver.mapper;

import com.okulservisi.backend.driver.dto.response.DriverResponse;
import com.okulservisi.backend.driver.dto.request.DriverUpdateRequest;
import com.okulservisi.backend.driver.entity.Driver;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface DriverMapper {

    @Mapping(target = "userId", expression = "java(driver.getUser().getId())")
    @Mapping(target = "isActive", source = "active")
    DriverResponse toResponse(Driver driver);

    /** Kısmi güncelleme: null alanlar dokunulmadan bırakılır. {@code userId} bu DTO'da yok. */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "active", source = "isActive")
    void updateFromRequest(@MappingTarget Driver driver, DriverUpdateRequest request);
}
