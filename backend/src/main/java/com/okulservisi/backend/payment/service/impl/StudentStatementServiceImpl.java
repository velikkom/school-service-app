package com.okulservisi.backend.payment.service.impl;

import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.billing.entity.MonthlyCharge;
import com.okulservisi.backend.billing.repository.MonthlyChargeRepository;
import com.okulservisi.backend.payment.dto.response.StatementEntryResponse;
import com.okulservisi.backend.payment.dto.response.StudentStatementResponse;
import com.okulservisi.backend.payment.entity.Payment;
import com.okulservisi.backend.payment.entity.PaymentMethod;
import com.okulservisi.backend.payment.entity.PaymentStatus;
import com.okulservisi.backend.payment.repository.PaymentRepository;
import com.okulservisi.backend.payment.service.StudentStatementService;
import com.okulservisi.backend.student.policy.StudentAccessGuard;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * docs/API.md "13. Ödeme": {@code GET /api/students/{id}/statement} — o öğrencinin TÜM
 * monthly_charge (borç/tahakkuk) + payment (ödeme) kayıtlarını KRONOLOJİK birleştirip her satırda
 * KOŞAN bakiyeyi (running balance) hesaplayan bir yanıt.
 */
@Service
@RequiredArgsConstructor
public class StudentStatementServiceImpl implements StudentStatementService {

    private final MonthlyChargeRepository monthlyChargeRepository;
    private final PaymentRepository paymentRepository;
    private final StudentAccessGuard studentAccessGuard;

    @Override
    public StudentStatementResponse getStatement(Long studentId, AuthenticatedUser principal) {
        studentAccessGuard.assertReadable(studentId, principal);

        record RawEntry(String type, Instant occurredAt, String period, PaymentMethod method,
                PaymentStatus paymentStatus, BigDecimal amount) {
        }

        List<RawEntry> raw = new ArrayList<>();
        for (MonthlyCharge charge : monthlyChargeRepository.findByStudentId(studentId)) {
            raw.add(new RawEntry("CHARGE", charge.getCreatedAt(), charge.getPeriod(), null, null, charge.getAmount()));
        }
        for (Payment payment : paymentRepository.findByStudentIdOrderByCreatedAtAsc(studentId)) {
            raw.add(new RawEntry("PAYMENT", payment.getCreatedAt(), null, payment.getMethod(),
                    payment.getStatus(), payment.getAmount()));
        }
        raw.sort(Comparator.comparing(RawEntry::occurredAt).thenComparing(RawEntry::type));

        BigDecimal runningBalance = BigDecimal.ZERO;
        List<StatementEntryResponse> entries = new ArrayList<>(raw.size());
        for (RawEntry entry : raw) {
            if ("CHARGE".equals(entry.type())) {
                runningBalance = runningBalance.add(entry.amount());
            } else if (entry.paymentStatus() == PaymentStatus.COMPLETED) {
                runningBalance = runningBalance.subtract(entry.amount());
            }
            entries.add(new StatementEntryResponse(entry.type(), entry.occurredAt(), entry.period(),
                    entry.method(), entry.paymentStatus(), entry.amount(), runningBalance));
        }

        return new StudentStatementResponse(studentId, entries, runningBalance);
    }
}
