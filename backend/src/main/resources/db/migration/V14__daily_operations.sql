-- Rol bazlı günlük operasyon geliştirmeleri (2026-11-18, bkz. docs/API.md §21-§27).

-- 1) Sefer: planlanan kalkış saati (nullable — eski seferlerde yok), iptal gerekçesi.
ALTER TABLE trip
    ADD COLUMN planned_start_time  TIME,
    ADD COLUMN cancellation_reason VARCHAR(500),
    ADD COLUMN cancelled_at        TIMESTAMPTZ;

CREATE INDEX idx_trip_hostess_date_direction ON trip (hostess_id, business_date, direction);

-- 2) Bildirim: olay anahtarı. Aynı olay (event_key) aynı alıcıya aynı kanaldan ikinci kez yazılamaz.
ALTER TABLE notification
    ADD COLUMN event_key VARCHAR(200);

CREATE UNIQUE INDEX uq_notification_recipient_channel_event
    ON notification (recipient_user_id, channel, event_key)
    WHERE event_key IS NOT NULL;

-- 3) Sözleşme: öz-kayıt başvurusu reddedilirse gerekçe.
ALTER TABLE contract
    ADD COLUMN rejection_reason VARCHAR(500);

-- 4) Veli "servise binmeyecek" bildirimi. Resmi yoklama (attendance_event) DEĞİLDİR.
CREATE TABLE trip_absence_notice (
    id                  BIGSERIAL PRIMARY KEY,
    trip_id             BIGINT       NOT NULL REFERENCES trip (id),
    student_id          BIGINT       NOT NULL REFERENCES student (id),
    parent_id           BIGINT       NOT NULL REFERENCES parent (id),
    note                VARCHAR(500),
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    withdrawn_at        TIMESTAMPTZ,
    version             INTEGER      NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_trip_absence_notice_status CHECK (status IN ('ACTIVE', 'WITHDRAWN'))
);

-- Aynı öğrenci+sefer için tek AKTİF bildirim (eşzamanlı tekrar gönderimde de).
CREATE UNIQUE INDEX uq_trip_absence_notice_active
    ON trip_absence_notice (trip_id, student_id)
    WHERE status = 'ACTIVE';
CREATE INDEX idx_trip_absence_notice_student ON trip_absence_notice (student_id);

CREATE TRIGGER trg_trip_absence_notice_updated_at
    BEFORE UPDATE ON trip_absence_notice
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

-- 5) Sefer operasyon kayıtları (gecikme/arıza/yol kapanması/iptal talebi).
CREATE TABLE trip_incident (
    id                   BIGSERIAL PRIMARY KEY,
    trip_id              BIGINT       NOT NULL REFERENCES trip (id),
    type                 VARCHAR(30)  NOT NULL,
    status               VARCHAR(20)  NOT NULL,
    delay_minutes        INTEGER,
    note                 VARCHAR(500) NOT NULL,
    reported_by_user_id  BIGINT       NOT NULL REFERENCES app_user (id),
    resolution_note      VARCHAR(500),
    resolved_by_user_id  BIGINT       REFERENCES app_user (id),
    resolved_at          TIMESTAMPTZ,
    version              INTEGER      NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_trip_incident_type CHECK (type IN ('DELAY', 'BREAKDOWN', 'ROAD_CLOSURE', 'CANCELLATION_REQUEST')),
    CONSTRAINT chk_trip_incident_status CHECK (status IN ('RECORDED', 'OPEN', 'APPROVED', 'REJECTED')),
    CONSTRAINT chk_trip_incident_delay CHECK (delay_minutes IS NULL OR delay_minutes BETWEEN 1 AND 600)
);

-- Aynı sefer için tek AÇIK iptal talebi.
CREATE UNIQUE INDEX uq_trip_incident_open_cancellation
    ON trip_incident (trip_id)
    WHERE type = 'CANCELLATION_REQUEST' AND status = 'OPEN';
CREATE INDEX idx_trip_incident_trip ON trip_incident (trip_id);

CREATE TRIGGER trg_trip_incident_updated_at
    BEFORE UPDATE ON trip_incident
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

-- 6) Yoklama düzeltme / inceleme talepleri.
CREATE TABLE attendance_correction_request (
    id                    BIGSERIAL PRIMARY KEY,
    trip_id               BIGINT       NOT NULL REFERENCES trip (id),
    student_id            BIGINT       NOT NULL REFERENCES student (id),
    kind                  VARCHAR(20)  NOT NULL,
    source_status         VARCHAR(20)  NOT NULL,
    requested_status      VARCHAR(20),
    reason                VARCHAR(500) NOT NULL,
    status                VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    requested_by_user_id  BIGINT       NOT NULL REFERENCES app_user (id),
    decided_by_user_id    BIGINT       REFERENCES app_user (id),
    decided_at            TIMESTAMPTZ,
    decision_note         VARCHAR(500),
    applied_status        VARCHAR(20),
    version               INTEGER      NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_acr_kind CHECK (kind IN ('CORRECTION', 'REVIEW')),
    CONSTRAINT chk_acr_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT chk_acr_source_status CHECK (source_status IN ('NONE', 'BOARDED', 'DROPPED_OFF', 'ABSENT')),
    CONSTRAINT chk_acr_requested_status CHECK (requested_status IS NULL
        OR requested_status IN ('NONE', 'BOARDED', 'DROPPED_OFF', 'ABSENT')),
    CONSTRAINT chk_acr_applied_status CHECK (applied_status IS NULL
        OR applied_status IN ('NONE', 'BOARDED', 'DROPPED_OFF', 'ABSENT'))
);

-- Aynı sefer+öğrenci için tek BEKLEYEN talep.
CREATE UNIQUE INDEX uq_acr_pending
    ON attendance_correction_request (trip_id, student_id)
    WHERE status = 'PENDING';
CREATE INDEX idx_acr_requested_by ON attendance_correction_request (requested_by_user_id);
CREATE INDEX idx_acr_status ON attendance_correction_request (status);

CREATE TRIGGER trg_attendance_correction_request_updated_at
    BEFORE UPDATE ON attendance_correction_request
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

-- 7) attendance_event: onaylanan düzeltme bir kaydı SİLMEZ, "geçersiz" (voided) işaretler.
--    Benzersizlik kuralı artık sadece geçerli (voided olmayan) orijinal kayıtlar için geçerlidir.
ALTER TABLE attendance_event
    ADD COLUMN voided_at                TIMESTAMPTZ,
    ADD COLUMN voided_by_correction_id  BIGINT REFERENCES attendance_correction_request (id),
    ADD COLUMN created_by_correction_id BIGINT REFERENCES attendance_correction_request (id);

DROP INDEX uq_attendance_event_trip_student_type;
CREATE UNIQUE INDEX uq_attendance_event_trip_student_type
    ON attendance_event (trip_id, student_id, event_type)
    WHERE is_correction = FALSE AND voided_at IS NULL;

-- 8) Veli durak değişikliği talepleri.
CREATE TABLE stop_change_request (
    id                       BIGSERIAL PRIMARY KEY,
    student_id               BIGINT       NOT NULL REFERENCES student (id),
    parent_id                BIGINT       NOT NULL REFERENCES parent (id),
    requested_by_user_id     BIGINT       NOT NULL REFERENCES app_user (id),
    direction                VARCHAR(20)  NOT NULL,
    requested_stop_id        BIGINT       NOT NULL REFERENCES stop (id),
    effective_date           DATE         NOT NULL,
    reason                   VARCHAR(500),
    status                   VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    decision_note            VARCHAR(500),
    decided_by_user_id       BIGINT       REFERENCES app_user (id),
    decided_at               TIMESTAMPTZ,
    resulting_assignment_id  BIGINT       REFERENCES student_route_assignment (id),
    version                  INTEGER      NOT NULL DEFAULT 0,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_scr_direction CHECK (direction IN ('MORNING', 'AFTERNOON')),
    CONSTRAINT chk_scr_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

CREATE UNIQUE INDEX uq_scr_pending
    ON stop_change_request (student_id, direction)
    WHERE status = 'PENDING';
CREATE INDEX idx_scr_requested_by ON stop_change_request (requested_by_user_id);

CREATE TRIGGER trg_stop_change_request_updated_at
    BEFORE UPDATE ON stop_change_request
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
