package com.okulservisi.backend.billing.service;

import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.billing.entity.MonthlyCharge;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * docs/API.md "12. Aylık ücret / tahakkuk" (bkz. docs/RBAC_MATRIX.md, docs/DATABASE_DESIGN.md
 * "Sözleşme / Finans"). {@link #run} HEM {@code POST /api/billing/run} (manuel, ADMIN) HEM DE
 * {@link BillingScheduler} TARAFINDAN çağrılan TEK ortak metottur.
 */
public interface BillingService {

    BillingRunResult run(String period);

    /** {@link BillingScheduler} için: Europe/Istanbul saatine göre CARİ dönemi "YYYY-MM" olarak döner. */
    String currentPeriod();

    Page<MonthlyCharge> listCharges(Long studentId, String period, AuthenticatedUser principal, Pageable pageable);
}
