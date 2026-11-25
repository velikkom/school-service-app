package com.okulservisi.backend.school.controller;

import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.common.web.PageResponse;
import com.okulservisi.backend.common.web.SortAllowlist;
import com.okulservisi.backend.school.api.SchoolApiPaths;
import com.okulservisi.backend.school.dto.request.SchoolCreateRequest;
import com.okulservisi.backend.school.dto.response.SchoolResponse;
import com.okulservisi.backend.school.dto.request.SchoolUpdateRequest;
import com.okulservisi.backend.school.entity.School;
import com.okulservisi.backend.school.mapper.SchoolMapper;
import com.okulservisi.backend.school.service.SchoolService;
import com.okulservisi.backend.security.annotation.AdminOnly;
import com.okulservisi.backend.security.annotation.AdminOrParent;
import com.okulservisi.backend.user.entity.Role;
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
 * docs/API.md "20. Okul" (bkz. docs/RBAC_MATRIX.md) — ADMIN CRUD + PARENT salt-okunur liste
 * (§19'daki veli öz-kayıt sihirbazının "okul seç" adımı için). Vehicle modülüyle AYNI basit CRUD
 * deseni — multi-tenant/firma DEĞİL.
 *
 * <p>PARENT için {@code isActive} filtresi İSTEMCİDEN GELDİĞİ GİBİ ÇALIŞMAZ — her zaman {@code true}
 * ile OVERRIDE edilir (PARENT asla pasif bir okulu göremez, ADMIN için filtre serbesttir).
 */
@RestController
@RequestMapping(SchoolApiPaths.BASE)
@RequiredArgsConstructor
public class SchoolController {

    private static final Set<String> SORTABLE_FIELDS = Set.of("id", "name", "createdAt");

    private final SchoolService schoolService;
    private final SchoolMapper schoolMapper;

    @GetMapping
    @AdminOrParent
    public PageResponse<SchoolResponse> list(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) String q,
            @RequestParam(name = "isActive", required = false) Boolean isActive,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        Boolean effectiveIsActive = principal.getRole() == Role.PARENT ? Boolean.TRUE : isActive;
        Pageable pageable = SortAllowlist.buildPageable(page, size, sort, SORTABLE_FIELDS, "id,asc");
        Page<School> result = schoolService.list(q, effectiveIsActive, pageable);
        return PageResponse.of(result, result.getContent().stream().map(schoolMapper::toResponse).toList());
    }

    @GetMapping(SchoolApiPaths.BY_ID)
    @AdminOrParent
    public ResponseEntity<SchoolResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(schoolMapper.toResponse(schoolService.getById(id)));
    }

    @PostMapping
    @AdminOnly
    public ResponseEntity<SchoolResponse> create(@Valid @RequestBody SchoolCreateRequest request) {
        return ResponseEntity.ok(schoolMapper.toResponse(schoolService.create(request)));
    }

    @PatchMapping(SchoolApiPaths.BY_ID)
    @AdminOnly
    public ResponseEntity<SchoolResponse> update(@PathVariable Long id, @Valid @RequestBody SchoolUpdateRequest request) {
        return ResponseEntity.ok(schoolMapper.toResponse(schoolService.update(id, request)));
    }
}
