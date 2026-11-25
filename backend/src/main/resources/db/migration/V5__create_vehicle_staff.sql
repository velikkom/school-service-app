CREATE TABLE vehicle (
    id         BIGSERIAL PRIMARY KEY,
    plate      VARCHAR(20) NOT NULL,
    capacity   INTEGER     NOT NULL,
    model      VARCHAR(100),
    is_active  BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_vehicle_capacity_positive CHECK (capacity > 0)
);

-- Plaka normalize edilmiş (büyük harf, boşluksuz) halde benzersizdir; normalizasyon uygulama katmanında yapılır.
CREATE UNIQUE INDEX uq_vehicle_plate ON vehicle (plate);

CREATE TRIGGER trg_vehicle_updated_at
    BEFORE UPDATE ON vehicle
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE driver (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    license_no VARCHAR(50) NOT NULL,
    is_active  BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_driver_user_id UNIQUE (user_id)
);

CREATE TRIGGER trg_driver_updated_at
    BEFORE UPDATE ON driver
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE hostess (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    is_active  BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_hostess_user_id UNIQUE (user_id)
);

CREATE TRIGGER trg_hostess_updated_at
    BEFORE UPDATE ON hostess
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
