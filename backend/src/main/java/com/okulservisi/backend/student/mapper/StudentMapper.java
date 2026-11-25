package com.okulservisi.backend.student.mapper;

import com.okulservisi.backend.student.dto.response.StudentResponse;
import com.okulservisi.backend.student.dto.request.StudentUpdateRequest;
import com.okulservisi.backend.student.entity.Student;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/** Entity → response DTO dönüşümü (MapStruct). İstek DTO'ları, FK çözümlemesi gerektiğinden
 * (user/route/stop lookup gibi) service katmanında elle işlenir. */
@Mapper(componentModel = "spring")
public interface StudentMapper {

    @Mapping(target = "userId", expression = "java(student.getUser() == null ? null : student.getUser().getId())")
    @Mapping(target = "isActive", source = "active")
    @Mapping(target = "schoolId", source = "school.id")
    StudentResponse toResponse(Student student);

    /** Kısmi güncelleme: null alanlar dokunulmadan bırakılır (bkz. StudentService.update). {@code
     * school}/{@code schoolName} FK çözümlemesi gerektirdiği için burada DEĞİL, StudentService'teki
     * {@code applySchool}'da elle işlenir. */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "school", ignore = true)
    @Mapping(target = "schoolName", ignore = true)
    void updateFromRequest(@MappingTarget Student student, StudentUpdateRequest request);
}
