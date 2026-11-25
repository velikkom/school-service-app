CREATE TABLE student (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT REFERENCES app_user (id) ON DELETE SET NULL,
    full_name   VARCHAR(150) NOT NULL,
    birth_date  DATE         NOT NULL,
    tc_no       VARCHAR(11),
    school_name VARCHAR(150),
    grade       VARCHAR(30),
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_student_user_id ON student (user_id) WHERE user_id IS NOT NULL;
CREATE UNIQUE INDEX uq_student_tc_no ON student (tc_no) WHERE tc_no IS NOT NULL;
CREATE INDEX idx_student_is_active ON student (is_active);

CREATE TRIGGER trg_student_updated_at
    BEFORE UPDATE ON student
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE parent (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    full_name  VARCHAR(150) NOT NULL,
    phone      VARCHAR(20),
    tc_no      VARCHAR(11),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_parent_user_id UNIQUE (user_id)
);

CREATE UNIQUE INDEX uq_parent_tc_no ON parent (tc_no) WHERE tc_no IS NOT NULL;

CREATE TRIGGER trg_parent_updated_at
    BEFORE UPDATE ON parent
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE parent_student_link (
    id            BIGSERIAL PRIMARY KEY,
    parent_id     BIGINT       NOT NULL REFERENCES parent (id) ON DELETE CASCADE,
    student_id    BIGINT       NOT NULL REFERENCES student (id) ON DELETE CASCADE,
    relation_type VARCHAR(30)  NOT NULL,
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    revoked_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_parent_student_link UNIQUE (parent_id, student_id)
);

CREATE INDEX idx_parent_student_link_student_id ON parent_student_link (student_id);
CREATE INDEX idx_parent_student_link_parent_id ON parent_student_link (parent_id);
CREATE INDEX idx_parent_student_link_active ON parent_student_link (student_id) WHERE is_active = TRUE;

CREATE TRIGGER trg_parent_student_link_updated_at
    BEFORE UPDATE ON parent_student_link
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
