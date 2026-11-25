package com.okulservisi.backend.route.controller;

import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.common.web.PageResponse;
import com.okulservisi.backend.common.web.SortAllowlist;
import com.okulservisi.backend.route.api.RouteApiPaths;
import com.okulservisi.backend.route.dto.request.RouteCreateRequest;
import com.okulservisi.backend.route.dto.response.RouteResponse;
import com.okulservisi.backend.route.dto.request.RouteUpdateRequest;
import com.okulservisi.backend.route.entity.Route;
import com.okulservisi.backend.route.entity.RouteDirection;
import com.okulservisi.backend.route.mapper.RouteMapper;
import com.okulservisi.backend.route.service.RouteService;
import com.okulservisi.backend.security.annotation.AdminDriverOrHostess;
import com.okulservisi.backend.security.annotation.AdminOnly;
import jakarta.validation.Valid;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * docs/API.md "7. Güzergâh/Durak". Class-level {@code @PreAuthorize} varsayılan olarak ADMIN'dir;
 * {@code create}/{@code update} bu varsayılanı miras alır (ADMIN-only, değişmedi). (Faz F takip turu)
 * {@code list}/{@code getById} artık METOD seviyesinde DRIVER/HOSTESS'i de kapsayacak şekilde
 * genişletildi — docs/RBAC_MATRIX.md "DRIVER/HOSTESS 🟡 (kendi atanmış güzergâh, salt okunur)": bir
 * route'un bu personele "atanmış" sayılması, o personelin en az bir {@code Trip}'inin bu route'u
 * kullanması anlamına gelir (bkz. {@link RouteService}); kapsam dışı erişim {@code
 * StudentAccessGuard}/{@code TripAccessGuard} ile AYNI konvansiyonla 404 döner (403 DEĞİL — var/yok
 * bilgisi sızdırılmaz).
 */
@RestController
@RequestMapping(RouteApiPaths.ROUTE_BASE)
@RequiredArgsConstructor
@AdminOnly
public class RouteController {

    private static final Set<String> SORTABLE_FIELDS = Set.of("id", "name", "direction", "createdAt");

    private final RouteService routeService;
    private final RouteMapper routeMapper;

    @GetMapping
    @AdminDriverOrHostess
    public PageResponse<RouteResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) RouteDirection direction,
            @RequestParam(name = "isActive", required = false) Boolean isActive,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        Pageable pageable = SortAllowlist.buildPageable(page, size, sort, SORTABLE_FIELDS, "id,asc");
        Page<Route> result = routeService.list(q, direction, isActive, principal, pageable);
        return PageResponse.of(result, result.getContent().stream().map(routeMapper::toResponse).toList());
    }

    @GetMapping(RouteApiPaths.ROUTE_BY_ID)
    @AdminDriverOrHostess
    public ResponseEntity<RouteResponse> getById(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(routeMapper.toResponse(routeService.getByIdForPrincipal(id, principal)));
    }

    @PostMapping
    public ResponseEntity<RouteResponse> create(@Valid @RequestBody RouteCreateRequest request) {
        return ResponseEntity.ok(routeMapper.toResponse(routeService.create(request)));
    }

    @PatchMapping(RouteApiPaths.ROUTE_BY_ID)
    public ResponseEntity<RouteResponse> update(@PathVariable Long id, @RequestBody RouteUpdateRequest request) {
        return ResponseEntity.ok(routeMapper.toResponse(routeService.update(id, request)));
    }
}
