package com.okulservisi.backend.settings.service;

import com.okulservisi.backend.settings.dto.request.InstitutionSettingsUpdateRequest;
import com.okulservisi.backend.settings.entity.InstitutionSettings;

/**
 * docs/API.md "17. Kurum ayarları" (bkz. docs/RBAC_MATRIX.md: SADECE ADMIN).
 *
 * <p><b>Tasarım kararı — tek satır davranışı:</b> {@code institution_settings} tablosu V9 migration'ı
 * tarafından TEK bir satırla (id=1, bkz. docs/DATABASE_DESIGN.md) seed edilir.
 */
public interface InstitutionSettingsService {

    InstitutionSettings get();

    InstitutionSettings update(InstitutionSettingsUpdateRequest request);
}
