-- Okul (School) referans/CRUD modülü (2026-10-25, bkz. docs/API.md §20, docs/DATABASE_DESIGN.md).
-- Multi-tenant/firma DEĞİL - taşıma firmasının servis verdiği okulların ADMIN tarafından yönetilen
-- basit bir referans listesi (Vehicle modülüyle AYNI basit CRUD deseni).

CREATE TABLE school (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(200) NOT NULL,
    address    VARCHAR(255),
    phone      VARCHAR(20),
    is_active  BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- name büyük/küçük harf duyarsız benzersizdir (docs/API.md §20).
CREATE UNIQUE INDEX uq_school_name ON school (LOWER(name));

CREATE TRIGGER trg_school_updated_at
    BEFORE UPDATE ON school
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

-- student: nullable FK - mevcut school_name kolonu DOKUNULMADAN kalır (geriye dönük uyum, bkz.
-- docs/API.md §19/§3: ADMIN'in doğrudan oluşturduğu öğrencilerde schoolId null kalabilir).
ALTER TABLE student
    ADD COLUMN school_id BIGINT REFERENCES school (id);
