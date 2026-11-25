package com.okulservisi.backend.route.service;

import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.route.dto.request.StopCreateRequest;
import com.okulservisi.backend.route.dto.request.StopUpdateRequest;
import com.okulservisi.backend.route.entity.Stop;
import java.util.List;

/**
 * docs/API.md "7. Güzergâh/Durak" — Stop create/update/delete ADMIN-only, {@code sequenceNo} route
 * içinde benzersiz/sıralı (docs/DATABASE_DESIGN.md UNIQUE(route_id, sequence_no)).
 */
public interface StopService {

    List<Stop> listByRoute(Long routeId);

    List<Stop> listByRoute(Long routeId, AuthenticatedUser principal);

    Stop getByIdAndRoute(Long routeId, Long stopId);

    Stop getByIdAndRoute(Long routeId, Long stopId, AuthenticatedUser principal);

    Stop create(Long routeId, StopCreateRequest request);

    Stop update(Long routeId, Long stopId, StopUpdateRequest request);

    void delete(Long routeId, Long stopId);
}
