package com.okulservisi.backend.user.mapper;

import com.okulservisi.backend.user.dto.response.UserResponse;
import com.okulservisi.backend.user.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "isActive", source = "active")
    UserResponse toResponse(User user);
}
