package com.okulservisi.backend.route.mapper;

import com.okulservisi.backend.route.dto.response.StopResponse;
import com.okulservisi.backend.route.entity.Stop;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface StopMapper {

    @Mapping(target = "routeId", expression = "java(stop.getRoute().getId())")
    StopResponse toResponse(Stop stop);
}
