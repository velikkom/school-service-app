package com.okulservisi.backend.auth.dto.response;

import com.okulservisi.backend.user.dto.response.UserResponse;

/** docs/API.md §18: approve → {@code { user: {...} } } (mevcut UserResponse şekli). */
public record RegistrationRequestApproveResponse(UserResponse user) {
}
