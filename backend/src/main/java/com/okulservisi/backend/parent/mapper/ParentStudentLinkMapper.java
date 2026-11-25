package com.okulservisi.backend.parent.mapper;

import com.okulservisi.backend.parent.dto.response.ParentStudentLinkResponse;
import com.okulservisi.backend.parent.entity.ParentStudentLink;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ParentStudentLinkMapper {

    @Mapping(target = "parentId", expression = "java(link.getParent().getId())")
    @Mapping(target = "studentId", expression = "java(link.getStudent().getId())")
    @Mapping(target = "isActive", source = "active")
    ParentStudentLinkResponse toResponse(ParentStudentLink link);
}
