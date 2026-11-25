package com.okulservisi.backend.contract.repository;
import com.okulservisi.backend.contract.entity.Contract;
import com.okulservisi.backend.contract.entity.ContractStatus;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * NOT (Faz G): {@code JpaSpecificationExecutor} ve {@link #findActiveForPeriod} Backend Agent
 * tarafından EKLENDİ (Faz F'teki {@code TripRepository} takip turuyla aynı desen — entity/repository'nin
 * geri kalanı değiştirilmedi). {@code findActiveForPeriod}, {@code BillingService.run}'ın belirli bir
 * {@code period} için GEÇERLİ ACTIVE sözleşmeleri bulması için gerekir.
 */
public interface ContractRepository extends JpaRepository<Contract, Long>, JpaSpecificationExecutor<Contract> {

    List<Contract> findByStudentId(Long studentId);

    /**
     * EKLENDİ (2026-10-25, veli öz-kayıt): {@code EnrollmentService}'in "tekrar-kayıt" engeli
     * (docs/API.md §19) için — bu veliye aktif bağlı ({@code ParentStudentLink.active=true})
     * öğrencilerden herhangi birinin {@code PENDING_APPROVAL}/{@code ACTIVE} durumda bir sözleşmesi
     * var mı kontrolü.
     */
    boolean existsByStudentIdInAndStatusIn(List<Long> studentIds, List<ContractStatus> statuses);

    /**
     * Aynı öğrenci için tarih aralığı çakışan ACTIVE sözleşme kontrolü (overlap).
     *
     * <p><b>DİKKAT — çağıran taraf {@code endDate == null} (süresiz sözleşme) durumunu ELLE ele
     * almalıdır:</b> bu sorgu {@code c.startDate <= :endDate} karşılaştırmasını {@code :endDate}
     * NULL olduğunda {@code StudentRouteAssignmentRepository.findOverlapping}'in aksine
     * {@code coalesce} İLE korumaz — JPQL'de {@code x <= NULL} her zaman UNKNOWN'dur, yani NULL
     * doğrudan geçirilirse hiçbir çakışma bulunamaz (sessiz yanlış negatif). Bu metot BİLEREK
     * değiştirilmedi (var olan repository, dokunulmaması istenen bir dosya); bunun yerine
     * {@code ContractService}, süresiz (`endDate == null`) sözleşmeler için {@code :endDate}
     * parametresine pratik bir "sonsuz" değer ({@link LocalDate#MAX}) geçirir — bu, "üst sınır yok"
     * anlamına doğru şekilde karşılık gelir ({@code c.startDate <= LocalDate.MAX} her zaman true).
     */
    @Query("select c from Contract c where c.student.id = :studentId and c.status = 'ACTIVE' "
            + "and c.id <> :excludingId "
            + "and c.startDate <= :endDate and (c.endDate is null or c.endDate >= :startDate)")
    List<Contract> findOverlappingActive(
            @Param("studentId") Long studentId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("excludingId") Long excludingId);

    /**
     * {@code BillingService.run} için: verilen dönemi (ayın ilk/son günü) KAPSAYAN her ACTIVE
     * sözleşme (docs/API.md "12. Aylık ücret/tahakkuk": {@code start_date<=period_sonu AND
     * (end_date IS NULL OR end_date>=period_başı)}).
     */
    @Query("select c from Contract c where c.status = 'ACTIVE' "
            + "and c.startDate <= :periodEnd and (c.endDate is null or c.endDate >= :periodStart)")
    List<Contract> findActiveForPeriod(
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd);
}
