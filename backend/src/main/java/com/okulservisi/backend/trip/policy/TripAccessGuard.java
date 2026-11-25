package com.okulservisi.backend.trip.policy;

import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.common.exception.NotFoundException;
import com.okulservisi.backend.student.policy.StudentScopePolicy;
import com.okulservisi.backend.user.entity.Role;
import com.okulservisi.backend.trip.entity.Trip;
import com.okulservisi.backend.trip.repository.TripRepository;
import com.okulservisi.backend.trip.repository.TripSpecifications;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * Nesne bazlı (ownership) yetkilendirme — DRIVER/HOSTESS personelinin SADECE kendi atandığı sefere
 * erişebildiğini doğrular (bkz. docs/RBAC_MATRIX.md "Nesne bazlı erişim ilkesi" ve
 * {@code StudentAccessGuard} ile aynı konvansiyon: kapsam dışı erişim 403 DEĞİL 404 döner). Hem
 * {@link TripService} (start/complete/cancel) hem de {@code AttendanceEventService} (biniş/iniş/
 * devamsızlık kaydı) tarafından paylaşılır — aynı kural iki kez yazılmasın diye.
 */
@Component
@RequiredArgsConstructor
public class TripAccessGuard {

    private final TripRepository tripRepository;
    private final StudentScopePolicy studentScopePolicy;

    /**
     * @throws NotFoundException tripId bulunamıyorsa, veya principal ADMIN değilken (DRIVER/HOSTESS
     *     kendi atandığı sefer değilse, ya da başka bir rol ise) erişemiyorsa.
     */
    public void assertStaffAssigned(Long tripId, AuthenticatedUser principal) {
        if (principal.getRole() == Role.ADMIN) {
            if (!tripRepository.existsById(tripId)) {
                throw NotFoundException.of("Sefer", tripId);
            }
            return;
        }
        if (principal.getRole() != Role.DRIVER && principal.getRole() != Role.HOSTESS) {
            throw NotFoundException.of("Sefer", tripId);
        }
        Specification<Trip> ownership = principal.getRole() == Role.DRIVER
                ? TripSpecifications.assignedToDriverUser(principal.getUserId())
                : TripSpecifications.assignedToHostessUser(principal.getUserId());
        boolean accessible = tripRepository.exists(Specification.allOf(TripSpecifications.hasId(tripId), ownership));
        if (!accessible) {
            throw NotFoundException.of("Sefer", tripId);
        }
    }

    /**
     * EKLENDİ (Faz H — Tracking): {@code GET /api/tracking/trips/{tripId}/latest} okuma kapsamı için.
     * ADMIN/DRIVER/HOSTESS için {@link #assertStaffAssigned} ile BİREBİR aynı kural (duplicate
     * edilmedi); PARENT/STUDENT için ise {@code TripService.assertReadable} ile aynı mantığı
     * ({@link StudentScopePolicy#accessibleStudentIds} + {@link TripSpecifications#accessibleToStudents})
     * burada da uygular — TrackingService'in kendi PARENT/STUDENT kapsam kodunu BAĞIMSIZ ve YANLIŞLIKLA
     * farklı yazmaması için tek noktadan sağlanır. Sefer durumunun (IN_PROGRESS) kontrolü BURADA
     * YAPILMAZ — bu saf bir "sahiplik/ilişki" kontrolüdür, duruma göre 404 kararını çağıran taraf
     * (TrackingService) verir (docs/RBAC_MATRIX.md "PARENT/STUDENT 🟡 çocuğunun/kendi aktif seferi").
     *
     * @throws NotFoundException tripId bulunamıyorsa veya principal bu sefere erişemiyorsa.
     */
    public void assertReadableForTracking(Long tripId, AuthenticatedUser principal) {
        if (principal.getRole() == Role.PARENT || principal.getRole() == Role.STUDENT) {
            Set<Long> studentIds = studentScopePolicy.accessibleStudentIds(principal);
            boolean accessible = tripRepository.exists(Specification.allOf(
                    TripSpecifications.hasId(tripId), TripSpecifications.accessibleToStudents(studentIds)));
            if (!accessible) {
                throw NotFoundException.of("Sefer", tripId);
            }
            return;
        }
        assertStaffAssigned(tripId, principal);
    }
}
