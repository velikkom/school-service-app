CREATE TABLE audit_log (
    id             BIGSERIAL PRIMARY KEY,
    actor_user_id  BIGINT      REFERENCES app_user (id),
    action         VARCHAR(100) NOT NULL,
    entity_name    VARCHAR(100) NOT NULL,
    entity_id      BIGINT,
    detail         JSONB,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_log_entity ON audit_log (entity_name, entity_id);
CREATE INDEX idx_audit_log_actor_user_id ON audit_log (actor_user_id);
CREATE INDEX idx_audit_log_created_at ON audit_log (created_at DESC);

CREATE TRIGGER trg_audit_log_updated_at
    BEFORE UPDATE ON audit_log
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
