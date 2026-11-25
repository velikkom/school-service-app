package com.okulservisi.backend.common.exception;

import org.springframework.http.HttpStatus;

/** Rate-limit aşımı (docs/SECURITY.md — login/forgot-password/reset-password). */
public class TooManyRequestsException extends ApiException {

    public TooManyRequestsException(String message) {
        super(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS", message);
    }
}
