CREATE TABLE notification (
    id                 BIGSERIAL PRIMARY KEY,
    recipient_user_id  BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    channel            VARCHAR(20) NOT NULL,
    title               VARCHAR(200) NOT NULL,
    body               TEXT        NOT NULL,
    is_read            BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_notification_channel CHECK (channel IN ('IN_APP', 'EMAIL'))
);

CREATE INDEX idx_notification_recipient_id ON notification (recipient_user_id);
CREATE INDEX idx_notification_recipient_unread ON notification (recipient_user_id) WHERE is_read = FALSE;

CREATE TRIGGER trg_notification_updated_at
    BEFORE UPDATE ON notification
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE vehicle_location (
    id          BIGSERIAL PRIMARY KEY,
    trip_id     BIGINT        NOT NULL REFERENCES trip (id) ON DELETE CASCADE,
    lat         NUMERIC(9,6)  NOT NULL,
    lng         NUMERIC(9,6)  NOT NULL,
    recorded_at TIMESTAMPTZ   NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_vehicle_location_trip_recorded_at ON vehicle_location (trip_id, recorded_at DESC);

CREATE TRIGGER trg_vehicle_location_updated_at
    BEFORE UPDATE ON vehicle_location
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE institution_settings (
    id                 BIGSERIAL PRIMARY KEY,
    name               VARCHAR(200) NOT NULL,
    address            VARCHAR(255),
    phone              VARCHAR(20),
    report_header_note VARCHAR(255),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TRIGGER trg_institution_settings_updated_at
    BEFORE UPDATE ON institution_settings
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

-- Tek satırlık ayar tablosu: uygulama her zaman id=1 satırını okuyup güncelleyecek şekilde tasarlanmıştır.
INSERT INTO institution_settings (name, address, phone, report_header_note)
VALUES ('Okul Servisi', NULL, NULL, NULL);
