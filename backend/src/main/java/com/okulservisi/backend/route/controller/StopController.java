package com.okulservisi.backend.route.controller;

import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.route.api.RouteApiPaths;
import com.okulservisi.backend.route.dto.request.StopCreateRequest;
import com.okulservisi.backend.route.dto.response.StopResponse;
import com.okulservisi.backend.route.dto.request.StopUpdateRequest;
import com.okulservisi.backend.route.mapper.StopMapper;
import com.okulservisi.backend.route.service.StopService;
import com.okulservisi.backend.security.annotation.AdminDriverOrHostess;
import com.okulservisi.backend.security.annotation.AdminOnly;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * docs/API.md "7. Güzergâh/Durak" — {@code /api/routes/{id}/stops}, create/update/delete ADMIN-only.
 * Class-level {@code @PreAuthorize} varsayılan ADMIN'dir; {@code list}/{@code getById} method
 * seviyesinde DRIVER/HOSTESS'i de kapsayacak şekilde genişletildi — {@code RouteController}'daki AYNI
 * desen (kendi atanmış güzergâhı salt-okunur görme, kapsam dışı 404).
 */
@RestController
@RequestMapping(RouteApiPaths.STOP_BASE)
@RequiredArgsConstructor
@AdminOnly
public class StopController {

    private final StopService stopService;
    private final StopMapper stopMapper;

    @GetMapping
    @AdminDriverOrHostess
    public List<StopResponse> list(@PathVariable Long routeId, @AuthenticationPrincipal AuthenticatedUser principal) {
        return stopService.listByRoute(routeId, principal).stream().map(stopMapper::toResponse).toList();
    }

    @GetMapping(RouteApiPaths.STOP_BY_ID)
    @AdminDriverOrHostess
    public ResponseEntity<StopResponse> getById(@PathVariable Long routeId, @PathVariable Long stopId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(stopMapper.toResponse(stopService.getByIdAndRoute(routeId, stopId, principal)));
    }

    @PostMapping
    public ResponseEntity<StopResponse> create(@PathVariable Long routeId, @Valid @RequestBody StopCreateRequest request) {
        return ResponseEntity.ok(stopMapper.toResponse(stopService.create(routeId, request)));
    }

    @PatchMapping(RouteApiPaths.STOP_BY_ID)
    public ResponseEntity<StopResponse> update(@PathVariable Long routeId, @PathVariable Long stopId,
            @RequestBody StopUpdateRequest request) {
        return ResponseEntity.ok(stopMapper.toResponse(stopService.update(routeId, stopId, request)));
    }

    @DeleteMapping(RouteApiPaths.STOP_BY_ID)
    public ResponseEntity<Void> delete(@PathVariable Long routeId, @PathVariable Long stopId) {
        stopService.delete(routeId, stopId);
        return ResponseEntity.noContent().build();
    }
}
