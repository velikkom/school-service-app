package com.okulservisi.backend.settings.repository;
import com.okulservisi.backend.settings.entity.InstitutionSettings;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstitutionSettingsRepository extends JpaRepository<InstitutionSettings, Long> {

    /**
     * EKLENDİ (Faz H): {@code institution_settings} tek satırlıktır (V9 migration'ı id=1 satırını
     * seed eder, bkz. docs/DATABASE_DESIGN.md "Bildirim / Konum / Ayarlar"). {@code GET}/{@code PATCH
     * /api/settings} bu satırı id sırasına göre (en küçük id = ilk/tek satır) bulur; satır hiç yoksa
     * (beklenmeyen/savunma amaçlı durum) {@code InstitutionSettingsService} varsayılan bir satır
     * oluşturur.
     */
    Optional<InstitutionSettings> findFirstByOrderByIdAsc();
}
