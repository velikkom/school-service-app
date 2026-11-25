package com.okulservisi.backend.trip.controller;

import com.okulservisi.backend.attendance.service.AttendanceEventService;
import com.okulservisi.backend.attendance.dto.response.TripRosterEntryResponse;
import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.common.web.PageResponse;
import com.okulservisi.backend.common.web.SortAllowlist;
import com.okulservisi.backend.route.entity.RouteDirection;
import com.okulservisi.backend.security.annotation.AdminDriverOrHostess;
import com.okulservisi.backend.security.annotation.AdminOnly;
import com.okulservisi.backend.security.annotation.AnyRole;
import com.okulservisi.backend.trip.api.TripApiPaths;
import com.okulservisi.backend.trip.dto.request.TripCancelRequest;
import com.okulservisi.backend.trip.dto.request.TripCreateRequest;
import com.okulservisi.backend.trip.dto.request.TripUpdateRequest;
import com.okulservisi.backend.trip.service.TripIncidentService;
import com.okulservisi.backend.user.entity.Role;
import org.springframework.web.bind.annotation.PatchMapping;
import com.okulservisi.backend.trip.dto.request.TripSearchCriteria;
import com.okulservisi.backend.trip.dto.response.TripResponse;
import com.okulservisi.backend.trip.entity.Trip;
import com.okulservisi.backend.trip.entity.TripStatus;
import com.okulservisi.backend.trip.mapper.TripMapper;
import com.okulservisi.backend.trip.service.TripService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** docs/API.md "9. Sefer" (bkz. docs/RBAC_MATRIX.md). */
@RestController
@RequestMapping(TripApiPaths.BASE)
@RequiredArgsConstructor
public class TripController {

    private static final Set<String> SORTABLE_FIELDS = Set.of("id", "businessDate", "direction", "status", "createdAt");

    private final TripService tripService;
    private final TripMapper tripMapper;
    private final AttendanceEventService attendanceEventService;
    private final TripIncidentService tripIncidentService;

    @GetMapping
    @AnyRole
    public PageResponse<TripResponse> list(
            @RequestParam(required = false) Long routeId,
            @RequestParam(required = false) Long vehicleId,
            @RequestParam(required = false) Long driverId,
            @RequestParam(required = false) Long hostessId,
            @RequestParam(required = false) LocalDate businessDate,
            @RequestParam(required = false) RouteDirection direction,
            @RequestParam(required = false) TripStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        Pageable pageable = SortAllowlist.buildPageable(page, size, sort, SORTABLE_FIELDS, "businessDate,desc");
        TripSearchCriteria criteria = new TripSearchCriteria(
                routeId, vehicleId, driverId, hostessId, businessDate, direction, status);
        Page<Trip> result = tripService.list(criteria, principal, pageable);
        return PageResponse.of(result, result.getContent().stream().map(tripMapper::toResponse).toList());
    }

    /** {@code GET /api/trips/mine?businessDate=...} — SADECE DRIVER/HOSTESS, kendi atandığı seferler. */
    @GetMapping(TripApiPaths.MINE)
    @PreAuthorize("hasAnyRole('DRIVER','HOSTESS')")
    public List<TripResponse> mine(
            @RequestParam(required = false) LocalDate businessDate,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return tripService.listMine(principal, businessDate).stream().map(tripMapper::toResponse).toList();
    }

    @GetMapping(TripApiPaths.BY_ID)
    @AnyRole
    public ResponseEntity<TripResponse> getById(@PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        TripResponse response = tripMapper.toResponse(tripService.getByIdForPrincipal(id, principal));
        if (principal.getRole() == Role.ADMIN || principal.getRole() == Role.DRIVER
                || principal.getRole() == Role.HOSTESS) {
            response = response.withOpenCancellationRequestId(tripIncidentService.findOpenCancellationRequestId(id));
        }
        return ResponseEntity.ok(response);
    }

    /** docs/API.md §21 {@code PATCH /api/trips/{id}} — SADECE ADMIN, sadece PLANNED sefer. */
    @PatchMapping(TripApiPaths.BY_ID)
    @AdminOnly
    public ResponseEntity<TripResponse> update(@PathVariable Long id, @Valid @RequestBody TripUpdateRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(tripMapper.toResponse(tripService.update(id, request, principal)));
    }

    @PostMapping
    @AdminOnly
    public ResponseEntity<TripResponse> create(@Valid @RequestBody TripCreateRequest request) {
        return ResponseEntity.ok(tripMapper.toResponse(tripService.create(request)));
    }

    @PostMapping(TripApiPaths.START)
    @AdminDriverOrHostess
    public ResponseEntity<TripResponse> start(@PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(tripMapper.toResponse(tripService.start(id, principal)));
    }

    @PostMapping(TripApiPaths.COMPLETE)
    @AdminDriverOrHostess
    public ResponseEntity<TripResponse> complete(@PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(tripMapper.toResponse(tripService.complete(id, principal)));
    }

    /**
     * 2026-11-18: doğrudan iptal SADECE ADMIN (DRIVER/HOSTESS 403 — iptal TALEBİ açarlar, bkz.
     * {@code TripIncidentController}). Body opsiyonel: {@code { reason }}.
     */
    @PostMapping(TripApiPaths.CANCEL)
    @AdminOnly
    public ResponseEntity<TripResponse> cancel(@PathVariable Long id,
            @Valid @RequestBody(required = false) TripCancelRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        String reason = request == null ? null : request.reason();
        return ResponseEntity.ok(tripMapper.toResponse(tripService.cancel(id, reason, principal)));
    }

    /**
     * {@code GET /api/trips/{id}/roster} — ADMIN tümü; DRIVER/HOSTESS sadece kendi atandığı sefer
     * (aksi halde 404, bkz. {@code AttendanceEventService#getRoster} / {@code TripAccessGuard}).
     * PARENT/STUDENT bu endpoint'e erişemez (kendi kapsamları {@code GET
     * /api/students/{id}/attendance} ile karşılanıyor, docs/API.md "9. Sefer").
     */
    @GetMapping(TripApiPaths.ROSTER)
    @AdminDriverOrHostess
    public List<TripRosterEntryResponse> roster(@PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return attendanceEventService.getRoster(id, principal);
    }
}
