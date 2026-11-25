package com.okulservisi.backend.payment.service;

import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.payment.dto.request.PaymentCreateRequest;
import com.okulservisi.backend.payment.entity.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** docs/API.md "13. Ödeme" (bkz. docs/RBAC_MATRIX.md, docs/DATABASE_DESIGN.md "Sözleşme / Finans"). */
public interface PaymentService {

    Payment create(PaymentCreateRequest request, String idempotencyKey, AuthenticatedUser principal);

    Payment cancel(Long id);

    Payment getByIdForPrincipal(Long id, AuthenticatedUser principal);

    Page<Payment> list(Long studentId, AuthenticatedUser principal, Pageable pageable);
}
