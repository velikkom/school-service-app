package com.okulservisi.backend.route.service;

import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.route.dto.request.RouteCreateRequest;
import com.okulservisi.backend.route.dto.request.RouteUpdateRequest;
import com.okulservisi.backend.route.entity.Route;
import com.okulservisi.backend.route.entity.RouteDirection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * docs/API.md "7. Güzergâh/Durak" — Route CRUD (ADMIN). DRIVER/HOSTESS kendi ATANMIŞ (en az bir
 * {@code Trip}'i olan) route'larını salt-okunur görebilir (docs/RBAC_MATRIX.md).
 */
public interface RouteService {

    Page<Route> list(String q, RouteDirection direction, Boolean isActive, AuthenticatedUser principal, Pageable pageable);

    Route getById(Long id);

    Route getByIdForPrincipal(Long id, AuthenticatedUser principal);

    Route create(RouteCreateRequest request);

    Route update(Long id, RouteUpdateRequest request);
}
