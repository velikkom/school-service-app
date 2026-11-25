-- Kayıt talebi + ADMIN onayı + sosyal giriş mimarisi (bkz. docs/SECURITY.md, docs/DATABASE_DESIGN.md
-- "Kullanıcı ve kimlik"). Üç tablo da aynı turda eklendiği ve birbirine FK ile bağlı olduğu için
-- tek migration dosyasında toplanmıştır.

CREATE TABLE registration_request (
    id                   BIGSERIAL PRIMARY KEY,
    first_name           VARCHAR(100) NOT NULL,
    last_name            VARCHAR(100) NOT NULL,
    email                VARCHAR(255) NOT NULL,
    password_hash        VARCHAR(255) NOT NULL,
    status               VARCHAR(30)  NOT NULL,
    email_verified_at    TIMESTAMPTZ,
    approved_at          TIMESTAMPTZ,
    approved_by_user_id  BIGINT REFERENCES app_user (id),
    rejected_at          TIMESTAMPTZ,
    rejection_reason     VARCHAR(500),
    created_user_id      BIGINT REFERENCES app_user (id),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_registration_request_status
        CHECK (status IN ('PENDING_EMAIL_VERIFICATION', 'EMAIL_VERIFIED', 'APPROVED', 'REJECTED'))
);

-- email UNIQUE DEĞİL (bkz. docs/DATABASE_DESIGN.md) - aynı e-posta ile zaman içinde birden fazla
-- satır olabilir (örn. REJECTED sonrası yeniden başvuru). "Aktif/tekrarsız talep" kuralı serviste
-- email+status bazlı sorgu ile uygulanır, bu yüzden ikisi birlikte indekslenir.
CREATE INDEX idx_registration_request_email_lower ON registration_request (lower(email));
CREATE INDEX idx_registration_request_status ON registration_request (status);

CREATE TRIGGER trg_registration_request_updated_at
    BEFORE UPDATE ON registration_request
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE registration_verification_token (
    id                        BIGSERIAL PRIMARY KEY,
    registration_request_id  BIGINT       NOT NULL REFERENCES registration_request (id) ON DELETE CASCADE,
    token_hash                VARCHAR(128) NOT NULL,
    expires_at                TIMESTAMPTZ  NOT NULL,
    used_at                   TIMESTAMPTZ,
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_registration_verification_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_registration_verification_token_request_id
    ON registration_verification_token (registration_request_id);
-- password_reset_token ile AYNI desen: kullanılmamış (aktif) token sorgusu için kısmi indeks.
CREATE INDEX idx_registration_verification_token_unused
    ON registration_verification_token (registration_request_id) WHERE used_at IS NULL;

CREATE TRIGGER trg_registration_verification_token_updated_at
    BEFORE UPDATE ON registration_verification_token
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE oauth_identity (
    id                        BIGSERIAL PRIMARY KEY,
    provider                  VARCHAR(20)  NOT NULL,
    provider_user_id          VARCHAR(255) NOT NULL,
    email                     VARCHAR(255),
    user_id                   BIGINT REFERENCES app_user (id) ON DELETE CASCADE,
    registration_request_id  BIGINT REFERENCES registration_request (id) ON DELETE CASCADE,
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_oauth_identity_provider CHECK (provider IN ('GOOGLE', 'FACEBOOK', 'X')),
    CONSTRAINT uq_oauth_identity_provider_user UNIQUE (provider, provider_user_id),
    -- Bir kimlik ya onaylanmış bir kullanıcıya ya da henüz onay bekleyen bir kayıt talebine
    -- bağlıdır, ikisine birden DEĞİL (bkz. docs/SECURITY.md "Sosyal giriş").
    CONSTRAINT chk_oauth_identity_exactly_one_owner CHECK (
        (user_id IS NOT NULL AND registration_request_id IS NULL)
        OR (user_id IS NULL AND registration_request_id IS NOT NULL)
    )
);

CREATE INDEX idx_oauth_identity_user_id ON oauth_identity (user_id);
CREATE INDEX idx_oauth_identity_registration_request_id ON oauth_identity (registration_request_id);

CREATE TRIGGER trg_oauth_identity_updated_at
    BEFORE UPDATE ON oauth_identity
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
