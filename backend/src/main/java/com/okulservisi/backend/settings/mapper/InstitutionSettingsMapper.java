package com.okulservisi.backend.settings.mapper;

import com.okulservisi.backend.settings.dto.response.InstitutionSettingsResponse;
import com.okulservisi.backend.settings.entity.InstitutionSettings;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface InstitutionSettingsMapper {

    InstitutionSettingsResponse toResponse(InstitutionSettings settings);
}
