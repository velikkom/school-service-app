package com.okulservisi.backend.payment.controller;

import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.common.web.PageResponse;
import com.okulservisi.backend.common.web.SortAllowlist;
import com.okulservisi.backend.payment.api.PaymentApiPaths;
import com.okulservisi.backend.payment.dto.request.PaymentCreateRequest;
import com.okulservisi.backend.payment.dto.response.PaymentResponse;
import com.okulservisi.backend.payment.entity.Payment;
import com.okulservisi.backend.payment.mapper.PaymentMapper;
import com.okulservisi.backend.payment.service.PaymentService;
import com.okulservisi.backend.security.annotation.AdminOnly;
import com.okulservisi.backend.security.annotation.AdminOrParent;
import jakarta.validation.Valid;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * docs/API.md "13. Ödeme" (bkz. docs/RBAC_MATRIX.md: DRIVER/HOSTESS/STUDENT tamamen ❌).
 *
 * <p><b>Tasarım kararı — {@code GET /api/payments}:</b> docs/API.md bu listeyi açıkça yazmıyor, ama
 * docs/RBAC_MATRIX.md PARENT satırında "kendi çocuğu, salt okunur + KENDİ ÖDEME GEÇMİŞİ" ifadesi
 * geçtiği ve ADMIN'in iptal edeceği ödemeyi önce bulabilmesi gerektiği için (Trip/Assignment CRUD
 * emsaliyle tutarlı) liste+detay endpoint'i eklendi.
 */
@RestController
@RequestMapping(PaymentApiPaths.PAYMENT_BASE)
@RequiredArgsConstructor
public class PaymentController {

    private static final Set<String> SORTABLE_FIELDS = Set.of("id", "amount", "status", "createdAt");

    private final PaymentService paymentService;
    private final PaymentMapper paymentMapper;

    @GetMapping
    @AdminOrParent
    public PageResponse<PaymentResponse> list(
            @RequestParam(required = false) Long studentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        Pageable pageable = SortAllowlist.buildPageable(page, size, sort, SORTABLE_FIELDS, "createdAt,desc");
        Page<Payment> result = paymentService.list(studentId, principal, pageable);
        return PageResponse.of(result, result.getContent().stream().map(paymentMapper::toResponse).toList());
    }

    @GetMapping(PaymentApiPaths.BY_ID)
    @AdminOrParent
    public ResponseEntity<PaymentResponse> getById(@PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(paymentMapper.toResponse(paymentService.getByIdForPrincipal(id, principal)));
    }

    @PostMapping
    @AdminOnly
    public ResponseEntity<PaymentResponse> create(
            @Valid @RequestBody PaymentCreateRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(paymentMapper.toResponse(paymentService.create(request, idempotencyKey, principal)));
    }

    @PostMapping(PaymentApiPaths.CANCEL)
    @AdminOnly
    public ResponseEntity<PaymentResponse> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(paymentMapper.toResponse(paymentService.cancel(id)));
    }
}
