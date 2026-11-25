package com.okulservisi.backend.parent.controller;

import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.common.web.PageResponse;
import com.okulservisi.backend.common.web.SortAllowlist;
import com.okulservisi.backend.parent.api.ParentApiPaths;
import com.okulservisi.backend.parent.dto.request.ParentCreateRequest;
import com.okulservisi.backend.parent.dto.response.ParentResponse;
import com.okulservisi.backend.parent.dto.request.ParentUpdateRequest;
import com.okulservisi.backend.parent.entity.Parent;
import com.okulservisi.backend.parent.mapper.ParentMapper;
import com.okulservisi.backend.parent.service.ParentService;
import com.okulservisi.backend.security.annotation.AdminOnly;
import com.okulservisi.backend.security.annotation.ParentOnly;
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

/** docs/API.md "4. Veli" (bkz. docs/RBAC_MATRIX.md). */
@RestController
@RequestMapping(ParentApiPaths.PARENT_BASE)
@RequiredArgsConstructor
public class ParentController {

    private static final Set<String> SORTABLE_FIELDS = Set.of("id", "fullName", "createdAt");

    private final ParentService parentService;
    private final ParentMapper parentMapper;

    @GetMapping
    @AdminOnly
    public PageResponse<ParentResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        Pageable pageable = SortAllowlist.buildPageable(page, size, sort, SORTABLE_FIELDS, "id,asc");
        Page<Parent> result = parentService.list(q, pageable);
        return PageResponse.of(result, result.getContent().stream().map(parentMapper::toResponse).toList());
    }

    @GetMapping(ParentApiPaths.ME)
    @ParentOnly
    public ResponseEntity<ParentResponse> me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(parentMapper.toResponse(parentService.getByUserId(principal.getUserId())));
    }

    @GetMapping(ParentApiPaths.BY_ID)
    @AdminOnly
    public ResponseEntity<ParentResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(parentMapper.toResponse(parentService.getById(id)));
    }

    @PostMapping
    @AdminOnly
    public ResponseEntity<ParentResponse> create(@Valid @RequestBody ParentCreateRequest request) {
        return ResponseEntity.ok(parentMapper.toResponse(parentService.create(request)));
    }

    @PatchMapping(ParentApiPaths.BY_ID)
    @AdminOnly
    public ResponseEntity<ParentResponse> update(@PathVariable Long id, @RequestBody ParentUpdateRequest request) {
        return ResponseEntity.ok(parentMapper.toResponse(parentService.update(id, request)));
    }
}
