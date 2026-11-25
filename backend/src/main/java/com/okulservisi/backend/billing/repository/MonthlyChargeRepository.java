package com.okulservisi.backend.billing.repository;
import com.okulservisi.backend.billing.entity.MonthlyCharge;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * NOT (Faz G): {@code JpaSpecificationExecutor} (liste/filtre için) ve {@link #sumAmountByStudentId}
 * (ödeme bakiyesi hesaplaması için, bkz. {@code PaymentService}) Backend Agent tarafından EKLENDİ.
 */
public interface MonthlyChargeRepository extends JpaRepository<MonthlyCharge, Long>, JpaSpecificationExecutor<MonthlyCharge> {

    Optional<MonthlyCharge> findByStudentIdAndPeriod(Long studentId, String period);

    boolean existsByStudentIdAndPeriod(Long studentId, String period);

    List<MonthlyCharge> findByStudentId(Long studentId);

    /** docs/DATABASE_DESIGN.md "Bakiye hesaplama": SUM(monthly_charge.amount) kısmı. */
    @Query("select coalesce(sum(m.amount), 0) from MonthlyCharge m where m.student.id = :studentId")
    BigDecimal sumAmountByStudentId(@Param("studentId") Long studentId);

    /**
     * EKLENDİ (Faz H — Reports): {@code GET /api/reports/finance/receivables} — TÜM öğrenciler için
     * toplam tahakkuk (bkz. {@code ReportService}, {@link #sumAmountByStudentId} ile AYNI toplam
     * mantığı, sadece studentId filtresi olmadan genel toplam).
     */
    @Query("select coalesce(sum(m.amount), 0) from MonthlyCharge m")
    BigDecimal sumAllAmounts();
}
