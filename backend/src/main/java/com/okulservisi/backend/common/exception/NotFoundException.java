package com.okulservisi.backend.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Kaynak bulunamadı veya kapsam dışı (örn. başka bir kullanıcının kaydı) — docs/API.md kuralı:
 * "bu ID var ama yetkin yok" bilgisi sızdırılmaz, her iki durumda da 404 döner.
 */
public class NotFoundException extends ApiException {

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    public static NotFoundException of(String entityName, Object id) {
        return new NotFoundException(entityName + " bulunamadı: " + id);
    }
}
