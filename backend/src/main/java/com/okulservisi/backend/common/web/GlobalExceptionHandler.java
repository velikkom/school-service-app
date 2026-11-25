package com.okulservisi.backend.common.web;

import com.okulservisi.backend.common.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * docs/API.md "Hata response formatı"nı tüm uygulama için tek noktadan üretir.
 * Stack trace, SQL veya iç sınıf adı hiçbir response'a yazılmaz; sadece sunucu loguna (DEBUG/ERROR) düşer.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException ex, HttpServletRequest request) {
        log.warn("İş kuralı hatası [{}] {}: {}", ex.getStatus(), request.getRequestURI(), ex.getMessage());
        ApiError body = ApiError.of(ex.getStatus().value(), ex.getErrorCode(), ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ApiError.FieldErrorItem> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldErrorItem(fe.getField(), fe.getDefaultMessage()))
                .toList();
        ApiError body = ApiError.of(HttpStatus.BAD_REQUEST.value(), "VALIDATION_ERROR",
                "Girilen bilgiler geçersiz.", request.getRequestURI(), fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class})
    public ResponseEntity<ApiError> handleBadRequest(Exception ex, HttpServletRequest request) {
        ApiError body = ApiError.of(HttpStatus.BAD_REQUEST.value(), "BAD_REQUEST",
                "İstek gövdesi/parametreleri geçersiz.", request.getRequestURI());
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * {@code @PreAuthorize} reddi (method security) {@link AccessDeniedException}
     * ({@code AuthorizationDeniedException} dahil, Spring Security 6.3+) fırlatır ve bu,
     * DispatcherServlet'in normal exception resolution akışında (bu advice içinde) yakalanır —
     * filter zincirindeki {@code ExceptionTranslationFilter}'a ulaşmaz. Bu handler olmadan
     * generic {@code Exception} handler'a düşer ve yanlışlıkla 500 dönerdi; burada doğru şekilde
     * 403 olarak işlenir (bkz. docs/API.md: yetkisiz-ama-kimliği-doğrulanmış istek -> 403).
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        ApiError body = ApiError.of(HttpStatus.FORBIDDEN.value(), "FORBIDDEN",
                "Bu işlemi yapmaya yetkiniz yok.", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    /** Aynı nedenle: filtre zincirine ulaşmayan kimlik doğrulama hataları için 401. */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthenticationException(AuthenticationException ex, HttpServletRequest request) {
        ApiError body = ApiError.of(HttpStatus.UNAUTHORIZED.value(), "UNAUTHORIZED",
                "Bu işlem için giriş yapmanız gerekiyor.", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Veri bütünlüğü ihlali {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        ApiError body = ApiError.of(HttpStatus.CONFLICT.value(), "DATA_INTEGRITY_VIOLATION",
                "İşlem veri bütünlüğü kuralını ihlal ediyor.", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    /**
     * Postgres {@code SERIALIZABLE} izolasyon seviyesinde eşzamanlı çakışan transaction'lar Spring
     * tarafından {@code CannotAcquireLockException}/{@code PessimisticLockingFailureException}
     * (ikisi de {@link ConcurrencyFailureException} alt sınıfı, SQLSTATE 40001) olarak fırlatılır
     * (bkz. {@code AssignmentService.create}/{@code update}, {@code Isolation.SERIALIZABLE}). Bu
     * handler olmadan generic {@code Exception} handler'a düşer ve yanlışlıkla 500 dönerdi; kaybeden
     * taraf için anlamlı olan yanıt 409 CONFLICT'tir (istemci tekrar denemelidir) - 500 (beklenmeyen/
     * genel hata) DEĞİL.
     */
    @ExceptionHandler(ConcurrencyFailureException.class)
    public ResponseEntity<ApiError> handleConcurrencyFailure(ConcurrencyFailureException ex, HttpServletRequest request) {
        log.warn("Eşzamanlılık çakışması {}: {}", request.getRequestURI(), ex.getMessage());
        ApiError body = ApiError.of(HttpStatus.CONFLICT.value(), "CONCURRENT_CONFLICT",
                "Bu işlem şu anda başka bir istekle çakıştı, lütfen tekrar deneyin.", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Beklenmeyen hata {}", request.getRequestURI(), ex);
        ApiError body = ApiError.of(HttpStatus.INTERNAL_SERVER_ERROR.value(), "INTERNAL_ERROR",
                "Beklenmeyen bir hata oluştu.", request.getRequestURI());
        return ResponseEntity.internalServerError().body(body);
    }
}
