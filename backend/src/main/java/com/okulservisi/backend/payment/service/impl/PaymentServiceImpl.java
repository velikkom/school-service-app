package com.okulservisi.backend.payment.service.impl;

import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.billing.repository.MonthlyChargeRepository;
import com.okulservisi.backend.common.exception.BadRequestException;
import com.okulservisi.backend.common.exception.ConflictException;
import com.okulservisi.backend.common.exception.NotFoundException;
import com.okulservisi.backend.payment.dto.request.PaymentCreateRequest;
import com.okulservisi.backend.payment.entity.Payment;
import com.okulservisi.backend.payment.entity.PaymentStatus;
import com.okulservisi.backend.payment.repository.PaymentRepository;
import com.okulservisi.backend.payment.repository.PaymentSpecifications;
import com.okulservisi.backend.payment.service.PaymentService;
import com.okulservisi.backend.student.entity.Student;
import com.okulservisi.backend.student.policy.StudentAccessGuard;
import com.okulservisi.backend.student.policy.StudentScopePolicy;
import com.okulservisi.backend.student.repository.StudentRepository;
import com.okulservisi.backend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * docs/API.md "13. Ödeme" (bkz. docs/RBAC_MATRIX.md, docs/DATABASE_DESIGN.md "Sözleşme / Finans").
 *
 * <p><b>Eşzamanlılık (fazla tahsilat önleme):</b> {@link StudentRepository#findByIdForUpdate} ile
 * pessimistic write lock uygulanır — aynı öğrenciye eşzamanlı 2 ödeme isteği, öğrenci satırındaki
 * kilit üzerinden sıraya girer.
 *
 * <p><b>Idempotency:</b> {@code Idempotency-Key} header'ı (opsiyonel) verilmişse önce {@code
 * PaymentRepository.findByIdempotencyKey} ile var olan kayıt aranır.
 */
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final MonthlyChargeRepository monthlyChargeRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final StudentAccessGuard studentAccessGuard;
    private final StudentScopePolicy studentScopePolicy;

    @Override
    @Transactional
    public Payment create(PaymentCreateRequest request, String idempotencyKey, AuthenticatedUser principal) {
        String normalizedKey = (idempotencyKey == null || idempotencyKey.isBlank()) ? null : idempotencyKey;

        if (normalizedKey != null) {
            Optional<Payment> existing = paymentRepository.findByIdempotencyKey(normalizedKey);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        Student student = studentRepository.findByIdForUpdate(request.studentId())
                .orElseThrow(() -> new BadRequestException("Belirtilen studentId'ye sahip öğrenci bulunamadı."));

        BigDecimal totalCharges = monthlyChargeRepository.sumAmountByStudentId(student.getId());
        BigDecimal totalCompletedPayments = paymentRepository.sumCompletedAmountByStudentId(student.getId());
        BigDecimal remaining = totalCharges.subtract(totalCompletedPayments);

        if (request.amount().compareTo(remaining) > 0) {
            throw new ConflictException(
                    "Ödeme tutarı kalan bakiyeden (" + remaining + ") fazla olamaz.");
        }

        Payment payment = new Payment();
        payment.setStudent(student);
        payment.setAmount(request.amount());
        payment.setMethod(request.method());
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setNote(request.note());
        payment.setIdempotencyKey(normalizedKey);
        payment.setCreatedBy(userRepository.getReferenceById(principal.getUserId()));

        try {
            return paymentRepository.save(payment);
        } catch (DataIntegrityViolationException ex) {
            if (normalizedKey != null) {
                return paymentRepository.findByIdempotencyKey(normalizedKey).orElseThrow(() -> ex);
            }
            throw ex;
        }
    }

    @Override
    @Transactional
    public Payment cancel(Long id) {
        Payment payment = getById(id);
        if (payment.getStatus() == PaymentStatus.CANCELLED) {
            throw new ConflictException("Ödeme zaten iptal edilmiş.");
        }
        payment.setStatus(PaymentStatus.CANCELLED);
        payment.setCancelledAt(Instant.now());
        return paymentRepository.save(payment);
    }

    @Override
    public Payment getByIdForPrincipal(Long id, AuthenticatedUser principal) {
        Payment payment = getById(id);
        studentAccessGuard.assertReadable(payment.getStudent().getId(), principal);
        return payment;
    }

    @Override
    public Page<Payment> list(Long studentId, AuthenticatedUser principal, Pageable pageable) {
        if (studentId != null) {
            studentAccessGuard.assertReadable(studentId, principal);
            return paymentRepository.findAll(PaymentSpecifications.hasStudentId(studentId), pageable);
        }
        Specification<Payment> spec = scopeSpecification(principal);
        return paymentRepository.findAll(spec, pageable);
    }

    private Specification<Payment> scopeSpecification(AuthenticatedUser principal) {
        Set<Long> ids = studentScopePolicy.accessibleStudentIds(principal);
        return ids == null ? null : PaymentSpecifications.studentIdIn(ids);
    }

    private Payment getById(Long id) {
        return paymentRepository.findById(id).orElseThrow(() -> NotFoundException.of("Ödeme", id));
    }
}
