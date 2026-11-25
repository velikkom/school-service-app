package com.okulservisi.backend.payment.api;

/**
 * docs/API.md "13. Ödeme" — payment domain'indeki iki controller'ın ({@code PaymentController},
 * {@code StudentStatementController}) mapping sabitleri.
 */
public final class PaymentApiPaths {

    private PaymentApiPaths() {
    }

    // --- PaymentController: /api/payments ---
    public static final String PAYMENT_BASE = "/api/payments";
    public static final String BY_ID = "/{id}";
    public static final String CANCEL = "/{id}/cancel";

    // --- StudentStatementController: /api/students/{studentId}/statement ---
    public static final String STUDENT_STATEMENT_BASE = "/api/students/{studentId}/statement";
}
