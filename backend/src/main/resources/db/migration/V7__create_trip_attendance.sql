CREATE TABLE trip (
    id           BIGSERIAL PRIMARY KEY,
    route_id     BIGINT      NOT NULL REFERENCES route (id),
    vehicle_id   BIGINT      NOT NULL REFERENCES vehicle (id),
    driver_id    BIGINT      NOT NULL REFERENCES driver (id),
    hostess_id   BIGINT      REFERENCES hostess (id),
    business_date DATE       NOT NULL,
    direction    VARCHAR(20) NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
    started_at   TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    version      INTEGER     NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_trip_direction CHECK (direction IN ('MORNING', 'AFTERNOON')),
    CONSTRAINT chk_trip_status CHECK (status IN ('PLANNED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT uq_trip_vehicle_date_direction UNIQUE (vehicle_id, business_date, direction),
    CONSTRAINT uq_trip_driver_date_direction UNIQUE (driver_id, business_date, direction)
);

CREATE INDEX idx_trip_route_id ON trip (route_id);
CREATE INDEX idx_trip_hostess_id ON trip (hostess_id);
CREATE INDEX idx_trip_business_date ON trip (business_date);

CREATE TRIGGER trg_trip_updated_at
    BEFORE UPDATE ON trip
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE attendance_event (
    id                   BIGSERIAL PRIMARY KEY,
    trip_id              BIGINT      NOT NULL REFERENCES trip (id),
    student_id           BIGINT      NOT NULL REFERENCES student (id),
    event_type           VARCHAR(20) NOT NULL,
    recorded_at          TIMESTAMPTZ NOT NULL,
    recorded_by_user_id  BIGINT      NOT NULL REFERENCES app_user (id),
    reason               VARCHAR(255),
    is_correction        BOOLEAN     NOT NULL DEFAULT FALSE,
    corrected_event_id   BIGINT      REFERENCES attendance_event (id),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_attendance_event_type CHECK (event_type IN ('BOARDING', 'DROPOFF', 'ABSENCE'))
);

-- Aynı sefer+öğrenci+tip için yalnızca bir "orijinal" (düzeltme olmayan) kayıt olabilir.
-- Düzeltmeler (is_correction = true) corrected_event_id ile orijinali referans alarak audit izli eklenir.
CREATE UNIQUE INDEX uq_attendance_event_trip_student_type
    ON attendance_event (trip_id, student_id, event_type)
    WHERE is_correction = FALSE;

CREATE INDEX idx_attendance_event_student_id ON attendance_event (student_id);
CREATE INDEX idx_attendance_event_trip_id ON attendance_event (trip_id);

CREATE TRIGGER trg_attendance_event_updated_at
    BEFORE UPDATE ON attendance_event
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
