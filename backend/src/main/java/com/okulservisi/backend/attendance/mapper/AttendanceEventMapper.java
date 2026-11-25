package com.okulservisi.backend.attendance.mapper;

import com.okulservisi.backend.attendance.dto.response.AttendanceEventResponse;
import com.okulservisi.backend.attendance.entity.AttendanceEvent;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AttendanceEventMapper {

    @Mapping(target = "tripId", expression = "java(event.getTrip().getId())")
    @Mapping(target = "studentId", expression = "java(event.getStudent().getId())")
    @Mapping(target = "recordedByUserId", expression = "java(event.getRecordedBy().getId())")
    AttendanceEventResponse toResponse(AttendanceEvent event);
}
