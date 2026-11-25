CREATE TABLE route (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(150) NOT NULL,
    direction  VARCHAR(20)  NOT NULL,
    is_active  BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_route_direction CHECK (direction IN ('MORNING', 'AFTERNOON'))
);

CREATE TRIGGER trg_route_updated_at
    BEFORE UPDATE ON route
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE stop (
    id           BIGSERIAL PRIMARY KEY,
    route_id     BIGINT       NOT NULL REFERENCES route (id) ON DELETE CASCADE,
    name         VARCHAR(150) NOT NULL,
    sequence_no  INTEGER      NOT NULL,
    planned_time TIME         NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_stop_route_sequence UNIQUE (route_id, sequence_no)
);

CREATE INDEX idx_stop_route_id ON stop (route_id);

CREATE TRIGGER trg_stop_updated_at
    BEFORE UPDATE ON stop
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE student_route_assignment (
    id         BIGSERIAL PRIMARY KEY,
    student_id BIGINT      NOT NULL REFERENCES student (id) ON DELETE CASCADE,
    route_id   BIGINT      NOT NULL REFERENCES route (id) ON DELETE CASCADE,
    stop_id    BIGINT      NOT NULL REFERENCES stop (id) ON DELETE CASCADE,
    direction  VARCHAR(20) NOT NULL,
    valid_from DATE        NOT NULL,
    valid_to   DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_student_route_assignment_direction CHECK (direction IN ('MORNING', 'AFTERNOON')),
    CONSTRAINT chk_student_route_assignment_dates CHECK (valid_to IS NULL OR valid_to >= valid_from)
);

-- Çakışma (overlap) kontrolü uygulama seviyesinde (serializable transaction) yapılır; bu indeks o sorguyu hızlandırır.
CREATE INDEX idx_student_route_assignment_student_direction
    ON student_route_assignment (student_id, direction);
CREATE INDEX idx_student_route_assignment_route_id ON student_route_assignment (route_id);
CREATE INDEX idx_student_route_assignment_stop_id ON student_route_assignment (stop_id);

CREATE TRIGGER trg_student_route_assignment_updated_at
    BEFORE UPDATE ON student_route_assignment
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
