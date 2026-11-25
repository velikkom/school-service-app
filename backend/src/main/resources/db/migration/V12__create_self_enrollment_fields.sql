-- Veli öz-kayıt (self-enrollment) sihirbazı için eklendi (2026-10-25, bkz. docs/API.md §19,
-- docs/DATABASE_DESIGN.md "Öğrenci / Veli" ve "Sözleşme / Finans" ve "Bildirim / Konum / Ayarlar").

-- student: yeni alanların TAMAMI nullable - ADMIN'in doğrudan oluşturduğu mevcut/yeni öğrencileri
-- etkilemez, sadece self-enrollment akışında uygulama seviyesinde zorunlu kılınır (DB seviyesinde değil).
ALTER TABLE student
    ADD COLUMN gender        VARCHAR(10),
    ADD COLUMN health_note   TEXT,
    ADD COLUMN city          VARCHAR(100),
    ADD COLUMN district      VARCHAR(100),
    ADD COLUMN neighborhood  VARCHAR(100),
    ADD COLUMN address_line  TEXT;

ALTER TABLE student
    ADD CONSTRAINT chk_student_gender CHECK (gender IN ('MALE', 'FEMALE'));

-- contract: PENDING_APPROVAL durumu eklendi (veli öz-kayıt formu gönderildiğinde bu durumla
-- oluşturulur; ADMIN approve/reject edene kadar ACTIVE sayılmaz, bkz. docs/API.md §19).
ALTER TABLE contract
    DROP CONSTRAINT chk_contract_status;

ALTER TABLE contract
    ADD CONSTRAINT chk_contract_status
        CHECK (status IN ('ACTIVE', 'CANCELLED', 'EXPIRED', 'PENDING_APPROVAL'));

-- institution_settings: self-enrollment akışında yeni sözleşmelerin başlangıç ücreti olarak
-- kullanılır (nullable - NULL iken self-enrollment 409 ile reddedilir).
ALTER TABLE institution_settings
    ADD COLUMN default_monthly_fee NUMERIC(12,2);

ALTER TABLE institution_settings
    ADD CONSTRAINT chk_institution_settings_default_monthly_fee_positive
        CHECK (default_monthly_fee > 0);
