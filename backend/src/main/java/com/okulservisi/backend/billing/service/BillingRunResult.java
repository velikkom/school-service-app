package com.okulservisi.backend.billing.service;

/** {@link BillingService#run} servis-katmanı sonucu; controller bunu {@code BillingRunResponse} DTO'suna eşler. */
public record BillingRunResult(String period, int created, int skipped) {
}
