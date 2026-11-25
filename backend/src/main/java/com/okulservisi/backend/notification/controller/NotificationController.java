package com.okulservisi.backend.notification.controller;

import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.common.web.PageResponse;
import com.okulservisi.backend.common.web.SortAllowlist;
import com.okulservisi.backend.notification.api.NotificationApiPaths;
import com.okulservisi.backend.notification.dto.request.NotificationBroadcastRequest;
import com.okulservisi.backend.notification.dto.response.NotificationBroadcastResponse;
import com.okulservisi.backend.notification.dto.response.NotificationResponse;
import com.okulservisi.backend.notification.entity.Notification;
import com.okulservisi.backend.notification.mapper.NotificationMapper;
import com.okulservisi.backend.notification.service.NotificationService;
import com.okulservisi.backend.security.annotation.AdminOnly;
import com.okulservisi.backend.security.annotation.AnyRole;
import jakarta.validation.Valid;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** docs/API.md "15. Bildirim" (bkz. docs/RBAC_MATRIX.md). */
@RestController
@RequestMapping(NotificationApiPaths.BASE)
@RequiredArgsConstructor
public class NotificationController {

    private static final Set<String> SORTABLE_FIELDS = Set.of("id", "createdAt");

    private final NotificationService notificationService;
    private final NotificationMapper notificationMapper;

    @GetMapping
    @AnyRole
    public PageResponse<NotificationResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        Pageable pageable = SortAllowlist.buildPageable(page, size, sort, SORTABLE_FIELDS, "createdAt,desc");
        Page<Notification> result = notificationService.listMine(principal, pageable);
        return PageResponse.of(result, result.getContent().stream().map(notificationMapper::toResponse).toList());
    }

    @PostMapping(NotificationApiPaths.READ)
    @AnyRole
    public ResponseEntity<NotificationResponse> markRead(@PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(notificationMapper.toResponse(notificationService.markRead(id, principal)));
    }

    @PostMapping(NotificationApiPaths.BROADCAST)
    @AdminOnly
    public ResponseEntity<NotificationBroadcastResponse> broadcast(
            @Valid @RequestBody NotificationBroadcastRequest request) {
        int recipientCount = notificationService.broadcast(request);
        return ResponseEntity.ok(new NotificationBroadcastResponse(recipientCount));
    }
}
