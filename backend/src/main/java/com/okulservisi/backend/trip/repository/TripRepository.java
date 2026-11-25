package com.okulservisi.backend.trip.repository;

import com.okulservisi.backend.route.entity.RouteDirection;
import com.okulservisi.backend.trip.entity.Trip;
import com.okulservisi.backend.trip.entity.TripStatus;
import java.time.LocalDate;
import java.util.List;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * NOT (Faz F): {@code JpaSpecificationExecutor}, iki {@code existsBy...} metodu ve (takip turu)
 * {@code findFirstByDriverIdAndStatusNotOrderByBusinessDateDescIdDesc} Backend Agent tarafından
 * EKLENDİ (Entity/Repository'nin geri kalanı değiştirilmedi) — docs/API.md "9. Sefer" liste/filtre +
 * çakışma ön-kontrolü (AssignmentService.assertNoOverlap deseniyle tutarlı, uq_trip_vehicle_date_direction
 * / uq_trip_driver_date_direction kısıtlarını DataIntegrityViolation olarak değil, anlamlı bir 409
 * ConflictException olarak yakalamak için) ve {@code GET /api/vehicles/mine} (bkz. VehicleService) bu
 * olmadan uygulanamazdı.
 */
public interface TripRepository extends JpaRepository<Trip, Long>, JpaSpecificationExecutor<Trip> {

    List<Trip> findByDriverIdAndBusinessDate(Long driverId, LocalDate businessDate);

    List<Trip> findByHostessIdAndBusinessDate(Long hostessId, LocalDate businessDate);

    boolean existsByVehicleIdAndBusinessDateAndDirection(Long vehicleId, LocalDate businessDate, RouteDirection direction);

    boolean existsByDriverIdAndBusinessDateAndDirection(Long driverId, LocalDate businessDate, RouteDirection direction);

    /** 2026-11-18: hostes çakışması (DB kısıtı yok — SERIALIZABLE transaction içinde ön-kontrol). */
    boolean existsByHostessIdAndBusinessDateAndDirection(Long hostessId, LocalDate businessDate, RouteDirection direction);

    /** 2026-11-18: sefer düzenleme — seferin KENDİSİ hariç çakışma kontrolleri. */
    boolean existsByVehicleIdAndBusinessDateAndDirectionAndIdNot(Long vehicleId, LocalDate businessDate,
            RouteDirection direction, Long id);

    boolean existsByDriverIdAndBusinessDateAndDirectionAndIdNot(Long driverId, LocalDate businessDate,
            RouteDirection direction, Long id);

    boolean existsByHostessIdAndBusinessDateAndDirectionAndIdNot(Long hostessId, LocalDate businessDate,
            RouteDirection direction, Long id);

    /**
     * 2026-11-18: durum geçişi/düzenleme/iptal için seferin satırını {@code SELECT ... FOR UPDATE} ile
     * kilitler. Kilit sırası her zaman: önce sefer satırı, sonra çakışma sorguları/bağlı tablolar —
     * eşzamanlı "başlat" ve "düzenle" istekleri bu satırda sıraya girer, ikinci istek birincinin
     * commit ettiği GÜNCEL durumu görür (bkz. TripServiceImpl).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Trip t where t.id = :id")
    Optional<Trip> findByIdForUpdate(@Param("id") Long id);

    /** Görüntüleme alanları (plaka, güzergâh adı, personel adı) için ilişkileri tek sorguda yükler. */
    @Override
    @EntityGraph(attributePaths = {"route", "vehicle", "driver", "driver.user", "hostess", "hostess.user"})
    Optional<Trip> findById(Long id);

    @Override
    @EntityGraph(attributePaths = {"route", "vehicle", "driver", "driver.user", "hostess", "hostess.user"})
    Page<Trip> findAll(Specification<Trip> spec, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"route", "vehicle", "driver", "driver.user", "hostess", "hostess.user"})
    List<Trip> findAll(Specification<Trip> spec, Sort sort);

    /** {@code GET /api/students/{id}/daily-service}: bir güzergâh+yön+tarih için sefer (en fazla bir tane beklenir). */
    @EntityGraph(attributePaths = {"route", "vehicle", "driver", "driver.user", "hostess", "hostess.user"})
    List<Trip> findByRouteIdAndDirectionAndBusinessDate(Long routeId, RouteDirection direction, LocalDate businessDate);

    /**
     * {@code GET /api/vehicles/mine} için: bu şoförün İPTAL EDİLMEMİŞ en güncel seferi (businessDate
     * DESC, eşitlikte id DESC) — "aktif atandığı araç" bu seferin aracı olarak tanımlanır (bkz.
     * VehicleService.getActiveVehicleForDriver javadoc'u, gerekçe orada).
     */
    Optional<Trip> findFirstByDriverIdAndStatusNotOrderByBusinessDateDescIdDesc(Long driverId, TripStatus excludedStatus);

    /**
     * NOT (Faz H — Reports): {@code GET /api/reports/operations/daily-summary?date=} için EKLENDİ —
     * belirtilen {@code businessDate} için toplam sefer sayısı ve durum bazında dağılım.
     */
    long countByBusinessDate(LocalDate businessDate);

    long countByBusinessDateAndStatus(LocalDate businessDate, TripStatus status);
}
