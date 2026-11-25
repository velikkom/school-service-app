package com.okulservisi.backend.security.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * docs/RBAC_MATRIX.md: ADMIN tam erişim, PARENT sadece kendi kapsamında (nesne bazlı kontrol
 * serviste — bkz. {@code StudentAccessGuard}/{@code StudentScopePolicy} ve eşdeğerleri).
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("hasAnyRole('ADMIN','PARENT')")
public @interface AdminOrParent {
}
