package com.okulservisi.backend.trip.service.impl;

import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.common.exception.BadRequestException;
import com.okulservisi.backend.common.exception.ConflictException;
import com.okulservisi.backend.common.exception.NotFoundException;
import com.okulservisi.backend.notification.service.OperationalNotificationService;
import com.okulservisi.backend.trip.dto.request.TripIncidentCreateRequest;
import com.okulservisi.backend.trip.dto.request.TripIncidentResolveRequest;
import com.okulservisi.backend.trip.dto.response.TripIncidentResponse;
import com.okulservisi.backend.trip.entity.Trip;
import com.okulservisi.backend.trip.entity.TripIncident;
import com.okulservisi.backend.trip.entity.TripIncidentStatus;
import com.okulservisi.backend.trip.entity.TripIncidentType;
import com.okulservisi.backend.trip.entity.TripStatus;
import com.okulservisi.backend.trip.policy.TripAccessGuard;
import com.okulservisi.backend.trip.policy.TripAudienceResolver;
import com.okulservisi.backend.trip.repository.TripIncidentRepository;
import com.okulservisi.backend.trip.service.TripIncidentService;
import com.okulservisi.backend.trip.service.TripLabels;
import com.okulservisi.backend.trip.service.TripService;
import com.okulservisi.backend.user.entity.Role;
import com.okulservisi.backend.user.entity.User;
import com.okulservisi.backend.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * docs/API.md §23. Kilit sırası {@link TripServiceImpl} ile AYNI: önce sefer satırı (FOR UPDATE), sonra
 * operasyon kaydı. Böylece aynı seferde eşzamanlı iki iptal talebi tek AÇIK talep üretir (ayrıca DB'de
 * {@code uq_trip_incident_open_cancellation}) ve "iptal talebini onayla" ile "seferi başlat/düzenle"
 * birbirini sırayla görür.
 */
@Service
@RequiredArgsConstructor
public class TripIncidentServiceImpl implements TripIncidentService {

    private final TripIncidentRepository incidentRepository;
    private final TripService tripService;
    private final TripAccessGuard tripAccessGuard;
    private final TripAudienceResolver audienceResolver;
    private final OperationalNotificationService notificationService;
    private final UserRepository userRepository;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public Result report(Long tripId, TripIncidentCreateRequest request, AuthenticatedUser principal) {
        tripAccessGuard.assertStaffAssigned(tripId, principal);
        Trip trip = tripService.lockById(tripId);
        if (trip.getStatus() != TripStatus.PLANNED && trip.getStatus() != TripStatus.IN_PROGRESS) {
            throw new ConflictException(
                    "Sadece planlanmış veya devam eden sefer için kayıt girilebilir (mevcut durum: "
                            + trip.getStatus() + ").");
        }
        if (request.type() == TripIncidentType.DELAY && request.delayMinutes() == null) {
            throw new BadRequestException("Gecikme kaydı için delayMinutes zorunludur.");
        }

        if (request.type() == TripIncidentType.CANCELLATION_REQUEST) {
            Optional<TripIncident> open = incidentRepository.findFirstByTripIdAndTypeAndStatus(
                    tripId, TripIncidentType.CANCELLATION_REQUEST, TripIncidentStatus.OPEN);
            if (open.isPresent()) {
                return new Result(toResponse(open.get()), false);
            }
        }

        User reporter = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new NotFoundException("Kullanıcı bulunamadı."));
        TripIncident incident = new TripIncident();
        incident.setTrip(trip);
        incident.setType(request.type());
        incident.setStatus(request.type() == TripIncidentType.CANCELLATION_REQUEST
                ? TripIncidentStatus.OPEN : TripIncidentStatus.RECORDED);
        incident.setDelayMinutes(request.type() == TripIncidentType.DELAY ? request.delayMinutes() : null);
        incident.setNote(request.note().trim());
        incident.setReportedBy(reporter);
        TripIncident saved = incidentRepository.saveAndFlush(incident);
        entityManager.refresh(saved);

        String label = TripLabels.describe(trip);
        String key = "trip-incident:" + saved.getId();
        switch (request.type()) {
            case DELAY -> notificationService.notify(audienceResolver.familyUsers(trip), "Servis gecikmesi",
                    label + " yaklaşık " + saved.getDelayMinutes() + " dakika gecikecek. Açıklama: " + saved.getNote(),
                    key);
            case BREAKDOWN, ROAD_CLOSURE -> notificationService.notify(audienceResolver.adminUsers(),
                    request.type() == TripIncidentType.BREAKDOWN ? "Araç arızası bildirildi" : "Yol kapanması bildirildi",
                    label + ": " + saved.getNote() + " (" + reporter.getFullName() + ")", key);
            case CANCELLATION_REQUEST -> notificationService.notify(audienceResolver.adminUsers(),
                    "Sefer iptal talebi", label + " için iptal talebi açıldı: " + saved.getNote()
                            + " (" + reporter.getFullName() + ")", key);
        }
        return new Result(toResponse(saved), true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TripIncidentResponse> list(Long tripId, AuthenticatedUser principal) {
        tripAccessGuard.assertStaffAssigned(tripId, principal);
        return incidentRepository.findByTripIdOrderByCreatedAtDescIdDesc(tripId).stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public TripIncidentResponse resolve(Long tripId, Long incidentId, TripIncidentResolveRequest request,
            AuthenticatedUser principal) {
        if (principal.getRole() != Role.ADMIN) {
            throw NotFoundException.of("Operasyon kaydı", incidentId);
        }
        Trip trip = tripService.lockById(tripId);
        TripIncident incident = incidentRepository.findByIdAndTripIdForUpdate(incidentId, tripId)
                .orElseThrow(() -> NotFoundException.of("Operasyon kaydı", incidentId));
        if (incident.getType() != TripIncidentType.CANCELLATION_REQUEST) {
            throw new ConflictException("Sadece iptal talepleri sonuçlandırılabilir.");
        }
        if (incident.getStatus() != TripIncidentStatus.OPEN) {
            throw new ConflictException("Bu iptal talebi zaten sonuçlandırılmış (" + incident.getStatus() + ").");
        }
        String note = request.note().trim();
        if (Boolean.TRUE.equals(request.approve())) {
            tripService.applyCancellation(trip, note);
            incident.setStatus(TripIncidentStatus.APPROVED);
        } else {
            incident.setStatus(TripIncidentStatus.REJECTED);
        }
        incident.setResolutionNote(note);
        incident.setResolvedBy(userRepository.getReferenceById(principal.getUserId()));
        incident.setResolvedAt(Instant.now());
        TripIncident saved = incidentRepository.saveAndFlush(incident);
        entityManager.refresh(saved);

        notificationService.notify(List.of(saved.getReportedBy()),
                Boolean.TRUE.equals(request.approve()) ? "İptal talebiniz onaylandı" : "İptal talebiniz reddedildi",
                TripLabels.describe(trip) + " için iptal talebiniz "
                        + (Boolean.TRUE.equals(request.approve()) ? "onaylandı" : "reddedildi") + ". Gerekçe: " + note,
                "trip-incident:" + saved.getId() + ":resolved");
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Long findOpenCancellationRequestId(Long tripId) {
        return incidentRepository.findFirstByTripIdAndTypeAndStatus(
                tripId, TripIncidentType.CANCELLATION_REQUEST, TripIncidentStatus.OPEN)
                .map(TripIncident::getId).orElse(null);
    }

    private TripIncidentResponse toResponse(TripIncident incident) {
        User reporter = incident.getReportedBy();
        User resolver = incident.getResolvedBy();
        return new TripIncidentResponse(
                incident.getId(),
                incident.getTrip().getId(),
                incident.getType(),
                incident.getStatus(),
                incident.getDelayMinutes(),
                incident.getNote(),
                reporter.getFullName(),
                reporter.getRole(),
                incident.getCreatedAt(),
                incident.getResolutionNote(),
                resolver == null ? null : resolver.getFullName(),
                incident.getResolvedAt());
    }
}
