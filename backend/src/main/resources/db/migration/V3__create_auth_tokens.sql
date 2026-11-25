CREATE TABLE refresh_token (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    token_hash VARCHAR(128) NOT NULL,
    expires_at TIMESTAMPTZ  NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_refresh_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_refresh_token_user_id ON refresh_token (user_id);
-- Aktif (iptal edilmemiş, süresi geçmemiş) token aramaları için kısmi indeks.
CREATE INDEX idx_refresh_token_active ON refresh_token (user_id) WHERE revoked_at IS NULL;

CREATE TRIGGER trg_refresh_token_updated_at
    BEFORE UPDATE ON refresh_token
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE password_reset_token (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    token_hash VARCHAR(128) NOT NULL,
    expires_at TIMESTAMPTZ  NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_password_reset_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_password_reset_token_user_id ON password_reset_token (user_id);
-- Kullanılmamış (aktif) token sorgusu için kısmi indeks (eşzamanlı çift kullanım kontrolü bu sorguyu hızlandırır).
CREATE INDEX idx_password_reset_token_unused ON password_reset_token (user_id) WHERE used_at IS NULL;

CREATE TRIGGER trg_password_reset_token_updated_at
    BEFORE UPDATE ON password_reset_token
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
