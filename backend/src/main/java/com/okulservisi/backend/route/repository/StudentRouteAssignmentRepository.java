package com.okulservisi.backend.route.repository;
import com.okulservisi.backend.route.entity.RouteDirection;
import com.okulservisi.backend.route.entity.StudentRouteAssignment;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentRouteAssignmentRepository extends JpaRepository<StudentRouteAssignment, Long>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<StudentRouteAssignment> {

    List<StudentRouteAssignment> findByStudentIdAndDirection(Long studentId, RouteDirection direction);

    boolean existsByStopId(Long stopId);

    boolean existsByRouteId(Long routeId);

    /**
     * Aynı öğrenci+yön için tarih aralığı çakışan (aktif, yani valid_to NULL veya gelecekte) başka bir
     * atama var mı — overlap kontrolü için kullanılır (çağıran taraf serializable transaction içinde çalışmalıdır).
     *
     * <p><b>Politika — sınırlar DAHİL (inclusive):</b> {@code <=} karşılaştırmaları kasıtlıdır; mevcut
     * bir atama {@code validTo=2026-01-10}'da bitiyorsa, yeni atama {@code validFrom=2026-01-10}'da
     * başlarsa bu ÇAKIŞMA sayılır (aynı gün için iki güzergâha atanamaz). {@code validTo=null} →
     * süresiz, {@code coalesce(..., 9999-12-31)} ile muamele edilir.
     */
    @Query("select a from StudentRouteAssignment a where a.student.id = :studentId and a.direction = :direction "
            + "and a.id <> :excludingId "
            + "and a.validFrom <= coalesce(:validTo, {d '9999-12-31'}) "
            + "and :validFrom <= coalesce(a.validTo, {d '9999-12-31'})")
    List<StudentRouteAssignment> findOverlapping(
            @Param("studentId") Long studentId,
            @Param("direction") RouteDirection direction,
            @Param("validFrom") LocalDate validFrom,
            @Param("validTo") LocalDate validTo,
            @Param("excludingId") Long excludingId);

    /**
     * {@code GET /api/trips/{id}/roster} — bir route+direction+businessDate için O TARİHTE aktif
     * (yani {@code validFrom <= businessDate} ve {@code coalesce(validTo, 9999-12-31) >= businessDate})
     * atamalar, durak sırasına ({@code stop.sequenceNo}) göre artan sırada. {@code findOverlapping}
     * ile AYNI null-safe karşılaştırma deseni kullanılır (bkz. yukarı).
     */
    @Query("select a from StudentRouteAssignment a where a.route.id = :routeId and a.direction = :direction "
            + "and a.validFrom <= :businessDate "
            + "and coalesce(a.validTo, {d '9999-12-31'}) >= :businessDate "
            + "order by a.stop.sequenceNo asc")
    List<StudentRouteAssignment> findActiveByRouteAndDirectionAndBusinessDate(
            @Param("routeId") Long routeId,
            @Param("direction") RouteDirection direction,
            @Param("businessDate") LocalDate businessDate);

    /**
     * 2026-11-18: bir öğrencinin belirli bir yön+tarihte AKTİF ataması/atamaları (en fazla bir tane
     * beklenir — overlap kuralı). Veli bildirimi/günlük servis/durak değişikliği için.
     */
    @Query("select a from StudentRouteAssignment a join fetch a.route join fetch a.stop "
            + "where a.student.id = :studentId and a.direction = :direction "
            + "and a.validFrom <= :date and coalesce(a.validTo, {d '9999-12-31'}) >= :date")
    List<StudentRouteAssignment> findActiveForStudentOnDate(
            @Param("studentId") Long studentId,
            @Param("direction") RouteDirection direction,
            @Param("date") LocalDate date);

    /** 2026-11-18: öğrencinin bir sefer (güzergâh+yön+tarih) yolcu listesinde olup olmadığı. */
    @Query("select count(a) > 0 from StudentRouteAssignment a where a.student.id = :studentId "
            + "and a.route.id = :routeId and a.direction = :direction "
            + "and a.validFrom <= :date and coalesce(a.validTo, {d '9999-12-31'}) >= :date")
    boolean isOnRoster(@Param("studentId") Long studentId, @Param("routeId") Long routeId,
            @Param("direction") RouteDirection direction, @Param("date") LocalDate date);
}
