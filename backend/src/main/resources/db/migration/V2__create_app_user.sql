CREATE TABLE app_user (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255)  NOT NULL,
    password_hash VARCHAR(255)  NOT NULL,
    full_name     VARCHAR(150)  NOT NULL,
    phone         VARCHAR(20),
    role          VARCHAR(20)   NOT NULL,
    is_active     BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_app_user_role CHECK (role IN ('ADMIN', 'DRIVER', 'HOSTESS', 'PARENT', 'STUDENT'))
);

-- Email benzersizliği büyük/küçük harf duyarsız (lower) olarak kontrol edilir.
CREATE UNIQUE INDEX uq_app_user_email_lower ON app_user (lower(email));

CREATE INDEX idx_app_user_role ON app_user (role);

CREATE TRIGGER trg_app_user_updated_at
    BEFORE UPDATE ON app_user
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
