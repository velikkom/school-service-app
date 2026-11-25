package com.okulservisi.backend.school.mapper;

import com.okulservisi.backend.school.dto.response.SchoolResponse;
import com.okulservisi.backend.school.dto.request.SchoolUpdateRequest;
import com.okulservisi.backend.school.entity.School;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface SchoolMapper {

    @Mapping(target = "isActive", source = "active")
    SchoolResponse toResponse(School school);

    /**
     * Kısmi güncelleme: null alanlar dokunulmadan bırakılır. {@code name} KASITLI OLARAK hariç
     * tutulur — benzersizlik kontrolü domain'e özgü mantıktır, SchoolService.update() içinde elle
     * (mapper çağrısından ÖNCE) yapılır (VehicleMapper'ın {@code plate} ile aynı deseni).
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "name", ignore = true)
    @Mapping(target = "active", source = "isActive")
    void updateFromRequest(@MappingTarget School school, SchoolUpdateRequest request);
}
