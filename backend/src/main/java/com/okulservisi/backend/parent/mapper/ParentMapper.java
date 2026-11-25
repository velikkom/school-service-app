package com.okulservisi.backend.parent.mapper;

import com.okulservisi.backend.parent.dto.response.ParentResponse;
import com.okulservisi.backend.parent.dto.request.ParentUpdateRequest;
import com.okulservisi.backend.parent.entity.Parent;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface ParentMapper {

    @Mapping(target = "userId", expression = "java(parent.getUser().getId())")
    ParentResponse toResponse(Parent parent);

    /** Kısmi güncelleme: null alanlar dokunulmadan bırakılır. {@code userId} bu DTO'da yok. */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateFromRequest(@MappingTarget Parent parent, ParentUpdateRequest request);
}
