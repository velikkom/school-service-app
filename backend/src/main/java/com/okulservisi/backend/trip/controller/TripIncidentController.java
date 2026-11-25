package com.okulservisi.backend.trip.controller;

import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.security.annotation.AdminDriverOrHostess;
import com.okulservisi.backend.security.annotation.AdminOnly;
import com.okulservisi.backend.trip.dto.request.TripIncidentCreateRequest;
import com.okulservisi.backend.trip.dto.request.TripIncidentResolveRequest;
import com.okulservisi.backend.trip.dto.response.TripIncidentResponse;
import com.okulservisi.backend.trip.service.TripIncidentService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** docs/API.md §23 — {@code /api/trips/{tripId}/incidents}. */
@RestController
@RequestMapping("/api/trips/{tripId}/incidents")
@RequiredArgsConstructor
public class TripIncidentController {

    private final TripIncidentService incidentService;

    @PostMapping
    @AdminDriverOrHostess
    public ResponseEntity<TripIncidentResponse> report(@PathVariable Long tripId,
            @Valid @RequestBody TripIncidentCreateRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        TripIncidentService.Result result = incidentService.report(tripId, request, principal);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.incident());
    }

    @GetMapping
    @AdminDriverOrHostess
    public List<TripIncidentResponse> list(@PathVariable Long tripId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return incidentService.list(tripId, principal);
    }

    @PostMapping("/{incidentId}/resolve")
    @AdminOnly
    public TripIncidentResponse resolve(@PathVariable Long tripId, @PathVariable Long incidentId,
            @Valid @RequestBody TripIncidentResolveRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return incidentService.resolve(tripId, incidentId, request, principal);
    }
}
