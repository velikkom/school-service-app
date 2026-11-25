package com.okulservisi.backend.trip.service;

import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.trip.dto.request.TripIncidentCreateRequest;
import com.okulservisi.backend.trip.dto.request.TripIncidentResolveRequest;
import com.okulservisi.backend.trip.dto.response.TripIncidentResponse;
import java.util.List;

/** docs/API.md §23 — sefer operasyon kayıtları ve iptal talepleri. */
public interface TripIncidentService {

    /** @return {@code created=false} ise zaten açık olan iptal talebi döndü. */
    Result report(Long tripId, TripIncidentCreateRequest request, AuthenticatedUser principal);

    List<TripIncidentResponse> list(Long tripId, AuthenticatedUser principal);

    TripIncidentResponse resolve(Long tripId, Long incidentId, TripIncidentResolveRequest request,
            AuthenticatedUser principal);

    Long findOpenCancellationRequestId(Long tripId);

    record Result(TripIncidentResponse incident, boolean created) {
    }
}
