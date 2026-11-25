package com.okulservisi.backend.settings.controller;
import com.okulservisi.backend.security.annotation.AdminOnly;
import com.okulservisi.backend.settings.api.InstitutionSettingsApiPaths;
import com.okulservisi.backend.settings.dto.response.InstitutionSettingsResponse;
import com.okulservisi.backend.settings.dto.request.InstitutionSettingsUpdateRequest;
import com.okulservisi.backend.settings.entity.InstitutionSettings;
import com.okulservisi.backend.settings.mapper.InstitutionSettingsMapper;
import com.okulservisi.backend.settings.service.InstitutionSettingsService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** docs/API.md "17. Kurum ayarları" (bkz. docs/RBAC_MATRIX.md: SADECE ADMIN, diğer tüm roller ❌). */
@RestController
@RequestMapping(InstitutionSettingsApiPaths.BASE)
@AdminOnly
@RequiredArgsConstructor
public class InstitutionSettingsController {

    private final InstitutionSettingsService institutionSettingsService;
    private final InstitutionSettingsMapper institutionSettingsMapper;

    @GetMapping
    public ResponseEntity<InstitutionSettingsResponse> get() {
        return ResponseEntity.ok(institutionSettingsMapper.toResponse(institutionSettingsService.get()));
    }

    @PatchMapping
    public ResponseEntity<InstitutionSettingsResponse> update(
            @Valid @RequestBody InstitutionSettingsUpdateRequest request) {
        return ResponseEntity.ok(institutionSettingsMapper.toResponse(institutionSettingsService.update(request)));
    }
}
