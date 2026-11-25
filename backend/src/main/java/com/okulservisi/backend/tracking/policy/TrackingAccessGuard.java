package com.okulservisi.backend.tracking.policy;

import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.common.exception.NotFoundException;
import com.okulservisi.backend.trip.entity.Trip;
import com.okulservisi.backend.trip.repository.TripRepository;
import com.okulservisi.backend.user.entity.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * docs/RBAC_MATRIX.md "{@code /api/tracking/**}" — konum GÖNDERME (POST) yetkisi {@code
 * TripAccessGuard#assertStaffAssigned}'dan BİLEREK FARKLI/AYRI bir kontroldür: o metot DRIVER VE
 * HOSTESS'i birlikte "personel" sayar (biniş/iniş/devamsızlık gibi ikisinin de yapabildiği işlemler
 * için doğrudur), ANCAK konum gönderme SADECE DRIVER'a aittir — HOSTESS sadece OKUYABİLİR, gönderemez
 * (docs/RBAC_MATRIX.md: "HOSTESS 🟡 kendi atanmış aktif sefer - SADECE oku, GÖNDEREMEZ — DRIVER'dan
 * farklı"). Mevcut guard'ı (ör. bir {@code assertStaffAssigned(tripId, principal, Role.DRIVER)}
 * overload'ı ekleyerek) GENİŞLETMEK yerine BAĞIMSIZ bir bileşen olarak yazıldı — koordinatör talimatı
 * ("mevcut guard'ı genişletmeden ayrı/ek bir kontrol yaz") gereği.
 */
@Component
@RequiredArgsConstructor
public class TrackingAccessGuard {

    private final TripRepository tripRepository;

    /**
     * @throws NotFoundException tripId bulunamıyorsa, principal DRIVER değilse, ya da bu sefere
     *     atanmış DRIVER principal değilse (kapsam dışı — 403 DEĞİL 404, bkz. docs/API.md "var/yok
     *     bilgisi sızdırılmaz" ilkesi).
     */
    public Trip assertDriverAssignedTrip(Long tripId, AuthenticatedUser principal) {
        Trip trip = tripRepository.findById(tripId).orElseThrow(() -> NotFoundException.of("Sefer", tripId));
        if (principal.getRole() != Role.DRIVER
                || !trip.getDriver().getUser().getId().equals(principal.getUserId())) {
            throw NotFoundException.of("Sefer", tripId);
        }
        return trip;
    }
}
