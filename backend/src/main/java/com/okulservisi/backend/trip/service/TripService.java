package com.okulservisi.backend.trip.service;

import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.trip.dto.request.TripCreateRequest;
import com.okulservisi.backend.trip.dto.request.TripSearchCriteria;
import com.okulservisi.backend.trip.dto.request.TripUpdateRequest;
import com.okulservisi.backend.trip.entity.Trip;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * docs/API.md "9. Sefer" ve §21 (bkz. docs/RBAC_MATRIX.md). Çakışma kuralı: aynı araç/şoför/hostes
 * aynı iş günü+yönde ikinci bir sefere atanamaz — SERIALIZABLE transaction içinde ön-kontrol (+ araç/
 * şoför için DB kısıtları).
 */
public interface TripService {

    Page<Trip> list(TripSearchCriteria criteria, AuthenticatedUser principal, Pageable pageable);

    List<Trip> listMine(AuthenticatedUser principal, LocalDate businessDate);

    Trip getByIdForPrincipal(Long id, AuthenticatedUser principal);

    Trip create(TripCreateRequest request);

    /** ADMIN — sadece PLANNED sefer; saat/araç/şoför/hostes. */
    Trip update(Long id, TripUpdateRequest request, AuthenticatedUser principal);

    Trip start(Long id, AuthenticatedUser principal);

    Trip complete(Long id, AuthenticatedUser principal);

    /** SADECE ADMIN (2026-11-18: DRIVER/HOSTESS doğrudan iptal yetkisi kaldırıldı — iptal TALEBİ açarlar). */
    Trip cancel(Long id, String reason, AuthenticatedUser principal);

    /**
     * Çağıranın transaction'ı içinde, sefer satırı ZATEN kilitliyken iptali uygular ve bildirimleri
     * yazar (ADMIN iptal talebini onayladığında da aynı kural/bildirim kullanılır).
     */
    Trip applyCancellation(Trip lockedTrip, String reason);

    /** Kilitli sefer satırı (yoksa 404) — çağıran transaction içinde. */
    Trip lockById(Long id);
}
