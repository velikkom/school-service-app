package com.okulservisi.backend.billing.service.impl;

import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.billing.entity.MonthlyCharge;
import com.okulservisi.backend.billing.repository.MonthlyChargeRepository;
import com.okulservisi.backend.billing.repository.MonthlyChargeSpecifications;
import com.okulservisi.backend.billing.service.BillingRunResult;
import com.okulservisi.backend.billing.service.BillingService;
import com.okulservisi.backend.common.exception.BadRequestException;
import com.okulservisi.backend.contract.entity.Contract;
import com.okulservisi.backend.contract.repository.ContractRepository;
import com.okulservisi.backend.student.policy.StudentAccessGuard;
import com.okulservisi.backend.student.policy.StudentScopePolicy;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * docs/API.md "12. Aylık ücret / tahakkuk" (bkz. docs/RBAC_MATRIX.md, docs/DATABASE_DESIGN.md
 * "Sözleşme / Finans"). {@link #run} HEM {@code POST /api/billing/run} (manuel, ADMIN) HEM DE
 * {@code BillingScheduler} (Europe/Istanbul, ayın 1'i 03:00, cari dönem) TARAFINDAN çağrılan TEK
 * ortak metottur — ikisi de aynı idempotency kuralına uyar (docs/API.md kuralı).
 */
@Service
@RequiredArgsConstructor
public class BillingServiceImpl implements BillingService {

    private static final Pattern PERIOD_PATTERN = Pattern.compile("^\\d{4}-(0[1-9]|1[0-2])$");
    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");

    private final ContractRepository contractRepository;
    private final MonthlyChargeRepository monthlyChargeRepository;
    private final StudentAccessGuard studentAccessGuard;
    private final StudentScopePolicy studentScopePolicy;
    private final Clock clock;

    @Override
    @Transactional
    public BillingRunResult run(String period) {
        YearMonth yearMonth = parsePeriod(period);
        LocalDate periodStart = yearMonth.atDay(1);
        LocalDate periodEnd = yearMonth.atEndOfMonth();

        List<Contract> activeContracts = contractRepository.findActiveForPeriod(periodStart, periodEnd);
        int created = 0;
        int skipped = 0;
        for (Contract contract : activeContracts) {
            Long studentId = contract.getStudent().getId();
            if (monthlyChargeRepository.existsByStudentIdAndPeriod(studentId, period)) {
                skipped++;
                continue;
            }
            MonthlyCharge charge = new MonthlyCharge();
            charge.setStudent(contract.getStudent());
            charge.setContract(contract);
            charge.setPeriod(period);
            charge.setAmount(contract.getMonthlyFee().subtract(contract.getDiscountAmount()));
            monthlyChargeRepository.save(charge);
            created++;
        }
        return new BillingRunResult(period, created, skipped);
    }

    @Override
    public String currentPeriod() {
        return YearMonth.now(clock.withZone(ISTANBUL)).toString();
    }

    @Override
    public Page<MonthlyCharge> listCharges(Long studentId, String period, AuthenticatedUser principal, Pageable pageable) {
        if (period != null && !PERIOD_PATTERN.matcher(period).matches()) {
            throw new BadRequestException("period 'YYYY-MM' formatında olmalıdır (örn. '2026-10').");
        }
        if (studentId != null) {
            studentAccessGuard.assertReadable(studentId, principal);
            Specification<MonthlyCharge> spec = Specification.allOf(
                    MonthlyChargeSpecifications.hasStudentId(studentId),
                    MonthlyChargeSpecifications.hasPeriod(period));
            return monthlyChargeRepository.findAll(spec, pageable);
        }
        Specification<MonthlyCharge> spec = Specification.allOf(
                MonthlyChargeSpecifications.hasPeriod(period),
                scopeSpecification(principal));
        return monthlyChargeRepository.findAll(spec, pageable);
    }

    private Specification<MonthlyCharge> scopeSpecification(AuthenticatedUser principal) {
        Set<Long> ids = studentScopePolicy.accessibleStudentIds(principal);
        return ids == null ? null : MonthlyChargeSpecifications.studentIdIn(ids);
    }

    private YearMonth parsePeriod(String period) {
        try {
            return YearMonth.parse(period);
        } catch (DateTimeParseException ex) {
            throw new BadRequestException("period 'YYYY-MM' formatında olmalıdır (örn. '2026-10').");
        }
    }
}
