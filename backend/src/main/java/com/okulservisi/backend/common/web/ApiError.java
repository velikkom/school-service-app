package com.okulservisi.backend.common.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/**
 * docs/API.md "Hata response formatı" ile tam uyumlu sabit hata şeması.
 * {@code fieldErrors} sadece validation hatalarında bulunur (null ise JSON'da hiç görünmez).
 * Stack trace, SQL veya iç sınıf adı asla içermez.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldErrorItem> fieldErrors) {

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(Instant.now(), status, error, message, path, null);
    }

    public static ApiError of(int status, String error, String message, String path, List<FieldErrorItem> fieldErrors) {
        return new ApiError(Instant.now(), status, error, message, path, fieldErrors);
    }

    public record FieldErrorItem(String field, String message) {
    }
}
