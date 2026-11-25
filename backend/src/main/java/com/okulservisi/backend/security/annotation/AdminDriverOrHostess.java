package com.okulservisi.backend.security.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * docs/RBAC_MATRIX.md: ADMIN tam erişim; DRIVER/HOSTESS sadece kendi atandığı kapsamda (nesne
 * bazlı kontrol serviste — bkz. {@code TripAccessGuard}, {@code RouteSpecifications.assignedToDriverUser}
 * ve eşdeğerleri).
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("hasAnyRole('ADMIN','DRIVER','HOSTESS')")
public @interface AdminDriverOrHostess {
}
