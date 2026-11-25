package com.okulservisi.backend.common.exception;

import org.springframework.http.HttpStatus;

/** Alan doğrulaması dışındaki genel geçersiz istek durumları (örn. izin verilmeyen sort alanı). */
public class BadRequestException extends ApiException {

    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }
}
