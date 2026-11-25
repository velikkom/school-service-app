package com.okulservisi.backend.auth.dto.response;

import com.okulservisi.backend.user.entity.Role;

public record UserSummaryDto(Long id, String fullName, String email, Role role) {
}
