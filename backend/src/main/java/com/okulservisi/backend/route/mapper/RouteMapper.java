package com.okulservisi.backend.route.mapper;

import com.okulservisi.backend.route.dto.response.RouteResponse;
import com.okulservisi.backend.route.dto.request.RouteUpdateRequest;
import com.okulservisi.backend.route.entity.Route;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface RouteMapper {

    @Mapping(target = "isActive", source = "active")
    RouteResponse toResponse(Route route);

    /** Kısmi güncelleme: null alanlar dokunulmadan bırakılır. */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "active", source = "isActive")
    void updateFromRequest(@MappingTarget Route route, RouteUpdateRequest request);
}
