CREATE TABLE contract (
    id               BIGSERIAL PRIMARY KEY,
    student_id       BIGINT         NOT NULL REFERENCES student (id),
    monthly_fee      NUMERIC(12,2)  NOT NULL,
    discount_amount  NUMERIC(12,2)  NOT NULL DEFAULT 0,
    start_date       DATE           NOT NULL,
    end_date         DATE,
    status           VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT chk_contract_monthly_fee_positive CHECK (monthly_fee > 0),
    CONSTRAINT chk_contract_discount_non_negative CHECK (discount_amount >= 0),
    CONSTRAINT chk_contract_status CHECK (status IN ('ACTIVE', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT chk_contract_dates CHECK (end_date IS NULL OR end_date >= start_date)
);

-- Aynı öğrenci için tarih aralığı çakışan iki ACTIVE sözleşme engeli uygulama seviyesinde
-- (serializable transaction) uygulanır; bu indeks o sorguyu hızlandırır.
CREATE INDEX idx_contract_student_status ON contract (student_id, status);

CREATE TRIGGER trg_contract_updated_at
    BEFORE UPDATE ON contract
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE monthly_charge (
    id          BIGSERIAL PRIMARY KEY,
    student_id  BIGINT        NOT NULL REFERENCES student (id),
    contract_id BIGINT        NOT NULL REFERENCES contract (id),
    period      VARCHAR(7)    NOT NULL,
    amount      NUMERIC(12,2) NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_monthly_charge_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_monthly_charge_period_format CHECK (period ~ '^\d{4}-\d{2}$'),
    CONSTRAINT uq_monthly_charge_student_period UNIQUE (student_id, period)
);

CREATE INDEX idx_monthly_charge_contract_id ON monthly_charge (contract_id);

CREATE TRIGGER trg_monthly_charge_updated_at
    BEFORE UPDATE ON monthly_charge
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE payment (
    id                  BIGSERIAL PRIMARY KEY,
    student_id          BIGINT        NOT NULL REFERENCES student (id),
    amount              NUMERIC(12,2) NOT NULL,
    method              VARCHAR(20)   NOT NULL,
    status              VARCHAR(20)   NOT NULL DEFAULT 'COMPLETED',
    idempotency_key     VARCHAR(100),
    note                VARCHAR(255),
    created_by_user_id  BIGINT        NOT NULL REFERENCES app_user (id),
    cancelled_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_payment_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_payment_method CHECK (method IN ('CASH', 'BANK_TRANSFER', 'CARD', 'OTHER')),
    CONSTRAINT chk_payment_status CHECK (status IN ('COMPLETED', 'CANCELLED'))
);

CREATE UNIQUE INDEX uq_payment_idempotency_key ON payment (idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX idx_payment_student_id ON payment (student_id);

CREATE TRIGGER trg_payment_updated_at
    BEFORE UPDATE ON payment
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
