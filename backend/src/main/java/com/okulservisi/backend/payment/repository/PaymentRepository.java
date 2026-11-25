package com.okulservisi.backend.payment.repository;
import com.okulservisi.backend.payment.entity.Payment;
import com.okulservisi.backend.payment.entity.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * NOT (Faz G): {@code JpaSpecificationExecutor} (liste/filtre için) ve
 * {@link #sumCompletedAmountByStudentId} (ödeme bakiyesi hesaplaması için) Backend Agent tarafından
 * EKLENDİ. NOT (Faz H): {@link #sumAllCompletedAmounts}, {@link #sumCompletedAmountBetween} ve
 * {@link #totalsByMethodBetween} (docs/API.md "14. Dashboard / Rapor" finans raporları için) EKLENDİ.
 */
public interface PaymentRepository extends JpaRepository<Payment, Long>, JpaSpecificationExecutor<Payment> {

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    List<Payment> findByStudentIdOrderByCreatedAtAsc(Long studentId);

    /** docs/DATABASE_DESIGN.md "Bakiye hesaplama": SUM(payment.amount WHERE status='COMPLETED') kısmı. */
    @Query("select coalesce(sum(p.amount), 0) from Payment p where p.student.id = :studentId and p.status = 'COMPLETED'")
    BigDecimal sumCompletedAmountByStudentId(@Param("studentId") Long studentId);

    /** {@code GET /api/reports/finance/receivables} — TÜM öğrenciler için toplam tamamlanmış ödeme. */
    @Query("select coalesce(sum(p.amount), 0) from Payment p where p.status = 'COMPLETED'")
    BigDecimal sumAllCompletedAmounts();

    /**
     * {@code GET /api/reports/finance/collections?from=&to=} — docs/API.md kuralı: "payment.createdAt
     * bazlı", SADECE {@code status=COMPLETED}. {@code from}/{@code to} çağıran tarafta (ReportService)
     * gün sınırları Instant'a çevrilerek verilir (from dahil, to hariç — bkz. ReportService javadoc'u).
     */
    @Query("select coalesce(sum(p.amount), 0) from Payment p "
            + "where p.status = 'COMPLETED' and p.createdAt >= :from and p.createdAt < :to")
    BigDecimal sumCompletedAmountBetween(@Param("from") Instant from, @Param("to") Instant to);

    /** Aynı rapor için yöntem (method) bazında kırılım. */
    @Query("select p.method as method, coalesce(sum(p.amount), 0) as total from Payment p "
            + "where p.status = 'COMPLETED' and p.createdAt >= :from and p.createdAt < :to group by p.method")
    List<PaymentMethodTotal> totalsByMethodBetween(@Param("from") Instant from, @Param("to") Instant to);

    /** {@link #totalsByMethodBetween} projeksiyonu. */
    interface PaymentMethodTotal {
        PaymentMethod getMethod();

        BigDecimal getTotal();
    }
}
