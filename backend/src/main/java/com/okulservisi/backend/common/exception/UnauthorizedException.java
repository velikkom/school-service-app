package com.okulservisi.backend.common.exception;

import org.springframework.http.HttpStatus;

/** Kimlik doğrulama başarısız (örn. login'de hatalı e-posta/şifre, geçersiz/süresi dolmuş token). */
public class UnauthorizedException extends ApiException {

    public UnauthorizedException(String message) {
        super(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message);
    }
}
