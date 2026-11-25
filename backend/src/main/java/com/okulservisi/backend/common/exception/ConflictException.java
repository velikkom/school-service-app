package com.okulservisi.backend.common.exception;

import org.springframework.http.HttpStatus;

/** İş kuralı ihlali nedeniyle isteğin gerçekleştirilemediği durumlar (örn. son aktif ADMIN korumalı). */
public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, "CONFLICT", message);
    }
}
