package com.okulservisi.backend.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Tüm özel iş kuralı hatalarının temel sınıfı. {@link com.okulservisi.backend.common.web.GlobalExceptionHandler}
 * bu tipi docs/API.md'deki sabit hata şemasına ({@code ApiError}) çevirir.
 */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public ApiException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }
}
