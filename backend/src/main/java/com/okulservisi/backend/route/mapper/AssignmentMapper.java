package com.okulservisi.backend.route.mapper;

import com.okulservisi.backend.route.dto.response.AssignmentResponse;
import com.okulservisi.backend.route.entity.StudentRouteAssignment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AssignmentMapper {

    @Mapping(target = "studentId", expression = "java(assignment.getStudent().getId())")
    @Mapping(target = "routeId", expression = "java(assignment.getRoute().getId())")
    @Mapping(target = "stopId", expression = "java(assignment.getStop().getId())")
    AssignmentResponse toResponse(StudentRouteAssignment assignment);
}
