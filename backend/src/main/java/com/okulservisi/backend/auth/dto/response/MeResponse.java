package com.okulservisi.backend.auth.dto.response;

import com.okulservisi.backend.user.entity.Role;

public record MeResponse(Long id, String fullName, String email, String phone, Role role) {
}
