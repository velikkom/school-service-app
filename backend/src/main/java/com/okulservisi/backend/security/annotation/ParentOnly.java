package com.okulservisi.backend.security.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/** docs/RBAC_MATRIX.md: endpoint sadece PARENT rolüne açıktır (nesne bazlı kapsam kontrolü serviste yapılır). */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("hasRole('PARENT')")
public @interface ParentOnly {
}
