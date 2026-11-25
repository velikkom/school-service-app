package com.okulservisi.backend.hostess.mapper;

import com.okulservisi.backend.hostess.dto.response.HostessResponse;
import com.okulservisi.backend.hostess.dto.request.HostessUpdateRequest;
import com.okulservisi.backend.hostess.entity.Hostess;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface HostessMapper {

    @Mapping(target = "userId", expression = "java(hostess.getUser().getId())")
    @Mapping(target = "isActive", source = "active")
    HostessResponse toResponse(Hostess hostess);

    /** Kısmi güncelleme: null alanlar dokunulmadan bırakılır. {@code userId} bu DTO'da yok. */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "active", source = "isActive")
    void updateFromRequest(@MappingTarget Hostess hostess, HostessUpdateRequest request);
}
