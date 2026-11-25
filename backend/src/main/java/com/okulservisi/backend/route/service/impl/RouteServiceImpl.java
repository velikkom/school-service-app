package com.okulservisi.backend.route.service.impl;

import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.common.exception.NotFoundException;
import com.okulservisi.backend.route.dto.request.RouteCreateRequest;
import com.okulservisi.backend.route.dto.request.RouteUpdateRequest;
import com.okulservisi.backend.route.entity.Route;
import com.okulservisi.backend.route.entity.RouteDirection;
import com.okulservisi.backend.route.mapper.RouteMapper;
import com.okulservisi.backend.route.repository.RouteRepository;
import com.okulservisi.backend.route.repository.RouteSpecifications;
import com.okulservisi.backend.route.service.RouteService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * docs/API.md "7. Güzergâh/Durak" — Route CRUD (ADMIN). DRIVER/HOSTESS kendi ATANMIŞ (en az bir
 * {@code Trip}'i olan) route'larını salt-okunur görebilir (docs/RBAC_MATRIX.md); kapsam dışı erişim
 * {@code StudentAccessGuard}/{@code TripAccessGuard} ile AYNI konvansiyonla 404 döner (403 DEĞİL).
 */
@Service
@RequiredArgsConstructor
public class RouteServiceImpl implements RouteService {

    private final RouteRepository routeRepository;
    private final RouteMapper routeMapper;

    @Override
    public Page<Route> list(String q, RouteDirection direction, Boolean isActive, AuthenticatedUser principal,
            Pageable pageable) {
        Specification<Route> spec = Specification.allOf(
                RouteSpecifications.matchesQuery(q),
                RouteSpecifications.hasDirection(direction),
                RouteSpecifications.isActive(isActive),
                scopeSpecification(principal));
        return routeRepository.findAll(spec, pageable);
    }

    private Specification<Route> scopeSpecification(AuthenticatedUser principal) {
        return switch (principal.getRole()) {
            case ADMIN -> null;
            case DRIVER -> RouteSpecifications.assignedToDriverUser(principal.getUserId());
            case HOSTESS -> RouteSpecifications.assignedToHostessUser(principal.getUserId());
            case PARENT, STUDENT -> RouteSpecifications.none();
        };
    }

    @Override
    public Route getById(Long id) {
        return routeRepository.findById(id).orElseThrow(() -> NotFoundException.of("Güzergâh", id));
    }

    @Override
    public Route getByIdForPrincipal(Long id, AuthenticatedUser principal) {
        Route route = getById(id);
        assertReadable(route, principal);
        return route;
    }

    private void assertReadable(Route route, AuthenticatedUser principal) {
        boolean accessible = switch (principal.getRole()) {
            case ADMIN -> true;
            case DRIVER -> routeRepository.exists(Specification.allOf(
                    RouteSpecifications.hasId(route.getId()), RouteSpecifications.assignedToDriverUser(principal.getUserId())));
            case HOSTESS -> routeRepository.exists(Specification.allOf(
                    RouteSpecifications.hasId(route.getId()), RouteSpecifications.assignedToHostessUser(principal.getUserId())));
            case PARENT, STUDENT -> false;
        };
        if (!accessible) {
            throw NotFoundException.of("Güzergâh", route.getId());
        }
    }

    @Override
    @Transactional
    public Route create(RouteCreateRequest request) {
        Route route = new Route();
        route.setName(request.name());
        route.setDirection(request.direction());
        route.setActive(true);
        return routeRepository.save(route);
    }

    @Override
    @Transactional
    public Route update(Long id, RouteUpdateRequest request) {
        Route route = getById(id);
        routeMapper.updateFromRequest(route, request);
        return routeRepository.save(route);
    }
}
