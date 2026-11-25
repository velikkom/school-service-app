package com.okulservisi.backend.settings.service.impl;

import com.okulservisi.backend.settings.dto.request.InstitutionSettingsUpdateRequest;
import com.okulservisi.backend.settings.entity.InstitutionSettings;
import com.okulservisi.backend.settings.repository.InstitutionSettingsRepository;
import com.okulservisi.backend.settings.service.InstitutionSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** docs/API.md "17. Kurum ayarları" (bkz. docs/RBAC_MATRIX.md: SADECE ADMIN). */
@Service
@RequiredArgsConstructor
public class InstitutionSettingsServiceImpl implements InstitutionSettingsService {

    private static final String DEFAULT_NAME = "Okul Servisi";

    private final InstitutionSettingsRepository institutionSettingsRepository;

    @Override
    @Transactional
    public InstitutionSettings get() {
        return getOrCreateDefault();
    }

    @Override
    @Transactional
    public InstitutionSettings update(InstitutionSettingsUpdateRequest request) {
        InstitutionSettings settings = getOrCreateDefault();
        if (request.name() != null) {
            settings.setName(request.name());
        }
        if (request.address() != null) {
            settings.setAddress(request.address());
        }
        if (request.phone() != null) {
            settings.setPhone(request.phone());
        }
        if (request.reportHeaderNote() != null) {
            settings.setReportHeaderNote(request.reportHeaderNote());
        }
        if (request.defaultMonthlyFee() != null) {
            settings.setDefaultMonthlyFee(request.defaultMonthlyFee());
        }
        return institutionSettingsRepository.save(settings);
    }

    private InstitutionSettings getOrCreateDefault() {
        return institutionSettingsRepository.findFirstByOrderByIdAsc().orElseGet(() -> {
            InstitutionSettings settings = new InstitutionSettings();
            settings.setName(DEFAULT_NAME);
            return institutionSettingsRepository.save(settings);
        });
    }
}
