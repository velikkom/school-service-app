package com.okulservisi.backend.security.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * docs/RBAC_MATRIX.md: tanımlı 5 rolün ({@code ADMIN/DRIVER/HOSTESS/PARENT/STUDENT}) tamamına açık
 * — giriş yapmış olmak yeterlidir, her rol kendi kapsamında görür (nesne bazlı kontrol serviste).
 * Rol listesi CLAUDE.md'ye göre sabittir, yeni rol türetilmez.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("hasAnyRole('ADMIN','DRIVER','HOSTESS','PARENT','STUDENT')")
public @interface AnyRole {
}
