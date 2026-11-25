package com.okulservisi.backend.auth.mapper;

import com.okulservisi.backend.auth.dto.response.RegistrationRequestResponse;
import com.okulservisi.backend.auth.entity.RegistrationRequest;
import org.mapstruct.Mapper;

/** docs/API.md §18 — {@code passwordHash} hiçbir zaman mapped/expose edilmez. */
@Mapper(componentModel = "spring")
public interface RegistrationRequestMapper {

    RegistrationRequestResponse toResponse(RegistrationRequest request);
}
