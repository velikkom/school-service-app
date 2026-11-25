package com.okulservisi.backend.trip.service.impl;

import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.common.exception.BadRequestException;
import com.okulservisi.backend.common.exception.ConflictException;
import com.okulservisi.backend.common.exception.NotFoundException;
import com.okulservisi.backend.driver.entity.Driver;
import com.okulservisi.backend.driver.repository.DriverRepository;
import com.okulservisi.backend.hostess.entity.Hostess;
import com.okulservisi.backend.hostess.repository.HostessRepository;
import com.okulservisi.backend.notification.service.OperationalNotificationService;
import com.okulservisi.backend.route.entity.Route;
import com.okulservisi.backend.route.entity.RouteDirection;
import com.okulservisi.backend.route.repository.RouteRepository;
import com.okulservisi.backend.student.policy.StudentScopePolicy;
import com.okulservisi.backend.trip.dto.request.TripCreateRequest;
import com.okulservisi.backend.trip.dto.request.TripSearchCriteria;
import com.okulservisi.backend.trip.dto.request.TripUpdateRequest;
import com.okulservisi.backend.trip.entity.Trip;
import com.okulservisi.backend.trip.entity.TripStatus;
import com.okulservisi.backend.trip.policy.TripAccessGuard;
import com.okulservisi.backend.trip.policy.TripAudienceResolver;
import com.okulservisi.backend.trip.repository.TripRepository;
import com.okulservisi.backend.trip.repository.TripSpecifications;
import com.okulservisi.backend.trip.service.TripLabels;
import com.okulservisi.backend.trip.service.TripService;
import com.okulservisi.backend.user.entity.Role;
import com.okulservisi.backend.user.entity.User;
import com.okulservisi.backend.vehicle.entity.Vehicle;
import com.okulservisi.backend.vehicle.repository.VehicleRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * docs/API.md "9. Sefer" + §21 (bkz. docs/RBAC_MATRIX.md). Çakışma kuralı (docs/DATABASE_DESIGN.md):
 * aynı araç/şoför/hostes aynı iş günü+yönde ikinci bir sefere atanamaz. Araç/şoför için DB kısıtları
 * ({@code uq_trip_vehicle_date_direction}/{@code uq_trip_driver_date_direction}) da vardır; kullanıcıya
 * anlamlı 409 göstermek için SERIALIZABLE transaction içinde gerçek ön-kontrol sorguları yapılır.
 *
 * <p><b>Kilit sırası (2026-11-18):</b> durum değiştiren/düzenleyen tüm işlemler önce sefer satırını
 * {@code SELECT ... FOR UPDATE} ile kilitler ({@link TripRepository#findByIdForUpdate}), durumu kilit
 * altında okunan GÜNCEL değere göre doğrular, sonra çakışma sorgularını ve yazmaları yapar. Eşzamanlı
 * "başlat" + "düzenle" isteklerinden ikincisi ya birincinin commit ettiği durumu görür (409 iş kuralı)
 * ya da PostgreSQL serileştirme hatası alır (409 CONCURRENT_CONFLICT) — hiçbir durumda başlamış bir
 * seferin ataması sessizce değişmez.
 */
@Service
@RequiredArgsConstructor
public class TripServiceImpl implements TripService {

    private final TripRepository tripRepository;
    private final RouteRepository routeRepository;
    private final VehicleRepository vehicleRepository;
    private final DriverRepository driverRepository;
    private final HostessRepository hostessRepository;
    private final StudentScopePolicy studentScopePolicy;
    private final TripAccessGuard tripAccessGuard;
    private final TripAudienceResolver audienceResolver;
    private final OperationalNotificationService notificationService;

    @Override
    public Page<Trip> list(TripSearchCriteria criteria, AuthenticatedUser principal, Pageable pageable) {
        Specification<Trip> spec = Specification.allOf(
                TripSpecifications.hasRouteId(criteria.routeId()),
                TripSpecifications.hasVehicleId(criteria.vehicleId()),
                TripSpecifications.hasDriverId(criteria.driverId()),
                TripSpecifications.hasHostessId(criteria.hostessId()),
                TripSpecifications.hasBusinessDate(criteria.businessDate()),
                TripSpecifications.hasDirection(criteria.direction()),
                TripSpecifications.hasStatus(criteria.status()),
                scopeSpecification(principal));
        return tripRepository.findAll(spec, pageable);
    }

    private Specification<Trip> scopeSpecification(AuthenticatedUser principal) {
        return switch (principal.getRole()) {
            case ADMIN -> null;
            case DRIVER -> TripSpecifications.assignedToDriverUser(principal.getUserId());
            case HOSTESS -> TripSpecifications.assignedToHostessUser(principal.getUserId());
            case PARENT, STUDENT -> {
                Set<Long> studentIds = studentScopePolicy.accessibleStudentIds(principal);
                yield TripSpecifications.accessibleToStudents(studentIds);
            }
        };
    }

    @Override
    public List<Trip> listMine(AuthenticatedUser principal, LocalDate businessDate) {
        Specification<Trip> ownership = principal.getRole() == Role.DRIVER
                ? TripSpecifications.assignedToDriverUser(principal.getUserId())
                : TripSpecifications.assignedToHostessUser(principal.getUserId());
        Specification<Trip> spec = Specification.allOf(ownership, TripSpecifications.hasBusinessDate(businessDate));
        return tripRepository.findAll(spec, Sort.by(Sort.Direction.ASC, "businessDate", "direction"));
    }

    @Override
    public Trip getByIdForPrincipal(Long id, AuthenticatedUser principal) {
        Trip trip = getById(id);
        assertReadable(trip, principal);
        return trip;
    }

    private void assertReadable(Trip trip, AuthenticatedUser principal) {
        boolean accessible = switch (principal.getRole()) {
            case ADMIN -> true;
            case DRIVER -> tripRepository.exists(Specification.allOf(
                    TripSpecifications.hasId(trip.getId()), TripSpecifications.assignedToDriverUser(principal.getUserId())));
            case HOSTESS -> tripRepository.exists(Specification.allOf(
                    TripSpecifications.hasId(trip.getId()), TripSpecifications.assignedToHostessUser(principal.getUserId())));
            case PARENT, STUDENT -> {
                Set<Long> studentIds = studentScopePolicy.accessibleStudentIds(principal);
                yield tripRepository.exists(Specification.allOf(
                        TripSpecifications.hasId(trip.getId()), TripSpecifications.accessibleToStudents(studentIds)));
            }
        };
        if (!accessible) {
            throw NotFoundException.of("Sefer", trip.getId());
        }
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Trip create(TripCreateRequest request) {
        Route route = routeRepository.findById(request.routeId())
                .orElseThrow(() -> new BadRequestException("Belirtilen routeId'ye sahip güzergâh bulunamadı."));
        Vehicle vehicle = vehicleRepository.findById(request.vehicleId())
                .orElseThrow(() -> new BadRequestException("Belirtilen vehicleId'ye sahip araç bulunamadı."));
        Driver driver = driverRepository.findById(request.driverId())
                .orElseThrow(() -> new BadRequestException("Belirtilen driverId'ye sahip şoför bulunamadı."));
        Hostess hostess = null;
        if (request.hostessId() != null) {
            hostess = hostessRepository.findById(request.hostessId())
                    .orElseThrow(() -> new BadRequestException("Belirtilen hostessId'ye sahip hostes bulunamadı."));
        }

        assertDirectionConsistent(route, request.direction());
        assertNoVehicleConflict(vehicle.getId(), request.businessDate(), request.direction(), null);
        assertNoDriverConflict(driver.getId(), request.businessDate(), request.direction(), null);
        if (hostess != null) {
            assertNoHostessConflict(hostess.getId(), request.businessDate(), request.direction(), null);
        }

        Trip trip = new Trip();
        trip.setRoute(route);
        trip.setVehicle(vehicle);
        trip.setDriver(driver);
        trip.setHostess(hostess);
        trip.setBusinessDate(request.businessDate());
        trip.setDirection(request.direction());
        trip.setPlannedStartTime(request.plannedStartTime());
        trip.setStatus(TripStatus.PLANNED);
        return initializeView(tripRepository.save(trip));
    }

    /**
     * docs/API.md §21 {@code PATCH /api/trips/{id}}. (1) sefer satırı FOR UPDATE, (2) durum kontrolü
     * İŞLEM ANINDA, (3) çakışma ön-kontrolleri (SERIALIZABLE), (4) kayıt + bildirimler aynı
     * transaction'da (rollback olursa bildirim de oluşmaz).
     */
    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Trip update(Long id, TripUpdateRequest request, AuthenticatedUser principal) {
        if (Boolean.TRUE.equals(request.removeHostess()) && request.hostessId() != null) {
            throw new BadRequestException("hostessId ve removeHostess birlikte gönderilemez.");
        }
        Trip trip = lockById(id);
        if (trip.getStatus() != TripStatus.PLANNED) {
            throw new ConflictException(
                    "Sadece PLANNED (henüz başlamamış) sefer düzenlenebilir (mevcut durum: " + trip.getStatus() + ").");
        }
        initializeView(trip);
        int previousVersion = trip.getVersion() == null ? 0 : trip.getVersion();
        String eventPrefix = "trip:" + trip.getId() + ":v" + previousVersion;

        LocalTime previousTime = trip.getPlannedStartTime();
        User previousDriverUser = trip.getDriver().getUser();
        User previousHostessUser = trip.getHostess() == null ? null : trip.getHostess().getUser();
        Long previousVehicleId = trip.getVehicle().getId();

        if (request.vehicleId() != null && !request.vehicleId().equals(trip.getVehicle().getId())) {
            Vehicle vehicle = vehicleRepository.findById(request.vehicleId())
                    .orElseThrow(() -> new BadRequestException("Belirtilen vehicleId'ye sahip araç bulunamadı."));
            assertNoVehicleConflict(vehicle.getId(), trip.getBusinessDate(), trip.getDirection(), trip.getId());
            trip.setVehicle(vehicle);
        }
        if (request.driverId() != null && !request.driverId().equals(trip.getDriver().getId())) {
            Driver driver = driverRepository.findById(request.driverId())
                    .orElseThrow(() -> new BadRequestException("Belirtilen driverId'ye sahip şoför bulunamadı."));
            assertNoDriverConflict(driver.getId(), trip.getBusinessDate(), trip.getDirection(), trip.getId());
            trip.setDriver(driver);
        }
        if (Boolean.TRUE.equals(request.removeHostess())) {
            trip.setHostess(null);
        } else if (request.hostessId() != null
                && (trip.getHostess() == null || !request.hostessId().equals(trip.getHostess().getId()))) {
            Hostess hostess = hostessRepository.findById(request.hostessId())
                    .orElseThrow(() -> new BadRequestException("Belirtilen hostessId'ye sahip hostes bulunamadı."));
            assertNoHostessConflict(hostess.getId(), trip.getBusinessDate(), trip.getDirection(), trip.getId());
            trip.setHostess(hostess);
        }
        if (request.plannedStartTime() != null) {
            trip.setPlannedStartTime(request.plannedStartTime());
        }

        Trip saved = initializeView(tripRepository.saveAndFlush(trip));
        String label = TripLabels.describe(saved);

        if (!Objects.equals(previousTime, saved.getPlannedStartTime())) {
            notificationService.notify(audienceResolver.familyUsers(saved), "Servis saati değişti",
                    label + " için planlanan kalkış saati " + TripLabels.time(previousTime) + " yerine "
                            + TripLabels.time(saved.getPlannedStartTime()) + " olarak güncellendi.",
                    eventPrefix + ":planned-time");
        }

        User currentDriverUser = saved.getDriver().getUser();
        User currentHostessUser = saved.getHostess() == null ? null : saved.getHostess().getUser();
        notifyStaffChange(previousDriverUser, currentDriverUser, "şoför", label, eventPrefix);
        notifyStaffChange(previousHostessUser, currentHostessUser, "hostes", label, eventPrefix);

        if (!previousVehicleId.equals(saved.getVehicle().getId())) {
            notificationService.notify(audienceResolver.staffUsers(saved), "Sefer aracı değişti",
                    label + " için araç " + saved.getVehicle().getPlate() + " olarak güncellendi.",
                    eventPrefix + ":vehicle");
        }
        return saved;
    }

    private void notifyStaffChange(User previous, User current, String roleLabel, String label, String eventPrefix) {
        Long previousId = previous == null ? null : previous.getId();
        Long currentId = current == null ? null : current.getId();
        if (Objects.equals(previousId, currentId)) {
            return;
        }
        if (previous != null) {
            notificationService.notify(List.of(previous), "Sefer atamanız kaldırıldı",
                    label + " için " + roleLabel + " atamanız kaldırıldı.",
                    eventPrefix + ":unassigned:" + previous.getId());
        }
        if (current != null) {
            notificationService.notify(List.of(current), "Yeni sefer ataması",
                    label + " için " + roleLabel + " olarak atandınız.",
                    eventPrefix + ":assigned:" + current.getId());
        }
    }

    @Override
    @Transactional
    public Trip start(Long id, AuthenticatedUser principal) {
        tripAccessGuard.assertStaffAssigned(id, principal);
        Trip trip = lockById(id);
        if (trip.getStatus() != TripStatus.PLANNED) {
            throw new ConflictException(
                    "Sefer PLANNED durumda değilken başlatılamaz (mevcut durum: " + trip.getStatus() + ").");
        }
        trip.setStatus(TripStatus.IN_PROGRESS);
        trip.setStartedAt(Instant.now());
        return initializeView(tripRepository.save(trip));
    }

    @Override
    @Transactional
    public Trip complete(Long id, AuthenticatedUser principal) {
        tripAccessGuard.assertStaffAssigned(id, principal);
        Trip trip = lockById(id);
        if (trip.getStatus() != TripStatus.IN_PROGRESS) {
            throw new ConflictException(
                    "Sefer IN_PROGRESS durumda değilken tamamlanamaz (mevcut durum: " + trip.getStatus() + ").");
        }
        trip.setStatus(TripStatus.COMPLETED);
        trip.setCompletedAt(Instant.now());
        return initializeView(tripRepository.save(trip));
    }

    @Override
    @Transactional
    public Trip cancel(Long id, String reason, AuthenticatedUser principal) {
        if (principal.getRole() != Role.ADMIN) {
            // Controller zaten @AdminOnly; servis seviyesinde de savunma (docs/RBAC_MATRIX.md).
            throw new AccessDeniedException("Seferi doğrudan iptal etme yetkisi sadece yöneticidedir.");
        }
        Trip trip = lockById(id);
        return applyCancellation(trip, reason);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Trip applyCancellation(Trip trip, String reason) {
        if (trip.getStatus() != TripStatus.PLANNED && trip.getStatus() != TripStatus.IN_PROGRESS) {
            throw new ConflictException(
                    "Sefer PLANNED veya IN_PROGRESS durumda değilken iptal edilemez (mevcut durum: "
                            + trip.getStatus() + ").");
        }
        String cleanReason = reason == null || reason.isBlank() ? null : reason.trim();
        trip.setStatus(TripStatus.CANCELLED);
        trip.setCancelledAt(Instant.now());
        trip.setCancellationReason(cleanReason);
        Trip saved = initializeView(tripRepository.save(trip));

        String label = TripLabels.describe(saved);
        String body = label + " iptal edildi." + (cleanReason == null ? "" : " Gerekçe: " + cleanReason);
        List<User> recipients = new ArrayList<>(audienceResolver.familyUsers(saved));
        recipients.addAll(audienceResolver.staffUsers(saved));
        notificationService.notify(recipients, "Sefer iptal edildi", body, "trip:" + saved.getId() + ":cancelled");
        return saved;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Trip lockById(Long id) {
        return tripRepository.findByIdForUpdate(id).orElseThrow(() -> NotFoundException.of("Sefer", id));
    }

    /** {@code open-in-view: false}: mapper'ın okuduğu ilişkileri transaction kapanmadan yükler. */
    private Trip initializeView(Trip trip) {
        Hibernate.initialize(trip.getRoute());
        Hibernate.initialize(trip.getVehicle());
        Hibernate.initialize(trip.getDriver());
        Hibernate.initialize(trip.getDriver().getUser());
        if (trip.getHostess() != null) {
            Hibernate.initialize(trip.getHostess());
            Hibernate.initialize(trip.getHostess().getUser());
        }
        return trip;
    }

    private void assertDirectionConsistent(Route route, RouteDirection direction) {
        if (route.getDirection() != direction) {
            throw new BadRequestException("Seferin yönü (direction), güzergâhın yönüyle eşleşmelidir.");
        }
    }

    private void assertNoVehicleConflict(Long vehicleId, LocalDate businessDate, RouteDirection direction,
            Long excludingTripId) {
        boolean conflict = excludingTripId == null
                ? tripRepository.existsByVehicleIdAndBusinessDateAndDirection(vehicleId, businessDate, direction)
                : tripRepository.existsByVehicleIdAndBusinessDateAndDirectionAndIdNot(
                        vehicleId, businessDate, direction, excludingTripId);
        if (conflict) {
            throw new ConflictException("Bu araç, bu tarih ve yönde zaten başka bir sefere atanmış.");
        }
    }

    private void assertNoDriverConflict(Long driverId, LocalDate businessDate, RouteDirection direction,
            Long excludingTripId) {
        boolean conflict = excludingTripId == null
                ? tripRepository.existsByDriverIdAndBusinessDateAndDirection(driverId, businessDate, direction)
                : tripRepository.existsByDriverIdAndBusinessDateAndDirectionAndIdNot(
                        driverId, businessDate, direction, excludingTripId);
        if (conflict) {
            throw new ConflictException("Bu şoför, bu tarih ve yönde zaten başka bir sefere atanmış.");
        }
    }

    private void assertNoHostessConflict(Long hostessId, LocalDate businessDate, RouteDirection direction,
            Long excludingTripId) {
        boolean conflict = excludingTripId == null
                ? tripRepository.existsByHostessIdAndBusinessDateAndDirection(hostessId, businessDate, direction)
                : tripRepository.existsByHostessIdAndBusinessDateAndDirectionAndIdNot(
                        hostessId, businessDate, direction, excludingTripId);
        if (conflict) {
            throw new ConflictException("Bu hostes, bu tarih ve yönde zaten başka bir sefere atanmış.");
        }
    }

    private Trip getById(Long id) {
        return tripRepository.findById(id).orElseThrow(() -> NotFoundException.of("Sefer", id));
    }
}
