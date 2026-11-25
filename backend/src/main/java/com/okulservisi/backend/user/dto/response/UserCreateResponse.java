package com.okulservisi.backend.user.dto.response;

/**
 * {@code user}: oluşturulan kullanıcı. {@code temporaryPassword}: sadece sunucu şifre ürettiğinde
 * (istek body'sinde {@code password} verilmediğinde) bir defalığına döner — tekrar gösterilmez.
 */
public record UserCreateResponse(UserResponse user, String temporaryPassword) {
}
