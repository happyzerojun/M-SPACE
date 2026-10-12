-- M-SPACE 초기 스키마 (docs/ERD.md v0.2)
-- 시각은 모두 timestamptz(UTC 저장)이고, "오늘"·운영 시간 판단은 애플리케이션이 Asia/Seoul로 해요.

-- 겹침 금지 EXCLUDE 제약에서 bigint(=)와 범위(&&)를 같은 GiST 인덱스로 묶으려면 필요해요 (RV-03)
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- ─────────────────────────────────────────────
-- 회원 (AU-01, AU-06~08)
-- ─────────────────────────────────────────────
CREATE TABLE users (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    student_no           VARCHAR(20)  NOT NULL,
    name                 VARCHAR(50)  NOT NULL,
    phone                VARCHAR(20)  NOT NULL,
    password_hash        VARCHAR(100) NOT NULL,
    role                 VARCHAR(20)  NOT NULL,
    status               VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    must_change_password BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMPTZ  NOT NULL,
    updated_at           TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_users_role CHECK (role IN ('STUDENT', 'ADMIN')),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

-- 비활성화된 계정의 학번으로 다시 가입할 수 있어야 해서 활성 계정에만 유니크를 걸어요 (AU-08)
CREATE UNIQUE INDEX uq_users_student_no_active ON users (student_no) WHERE status = 'ACTIVE';

-- ─────────────────────────────────────────────
-- 방 (AD-05). 삭제하지 않고 active=false로 비활성화해요
-- ─────────────────────────────────────────────
CREATE TABLE room (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       VARCHAR(30) NOT NULL,
    capacity   SMALLINT    NOT NULL,
    active     BOOLEAN     NOT NULL DEFAULT TRUE,
    sort_order SMALLINT    NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_room_name UNIQUE (name),
    CONSTRAINT ck_room_capacity CHECK (capacity > 0)
);

-- ─────────────────────────────────────────────
-- 예약 (RV-*, KY-*, NS-01, OP-*)
-- ─────────────────────────────────────────────
CREATE TABLE reservation (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    room_id       BIGINT       NOT NULL REFERENCES room (id),
    user_id       BIGINT       NOT NULL REFERENCES users (id),
    start_at      TIMESTAMPTZ  NOT NULL,
    end_at        TIMESTAMPTZ  NOT NULL,
    headcount     SMALLINT     NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    key_required  BOOLEAN      NOT NULL,
    key_issued_at TIMESTAMPTZ,
    returned_at   TIMESTAMPTZ,
    returned_late BOOLEAN,
    checked_in_at TIMESTAMPTZ,
    completed_at  TIMESTAMPTZ,
    cancelled_at  TIMESTAMPTZ,
    cancelled_by  BIGINT REFERENCES users (id),
    cancel_reason VARCHAR(200),
    no_show_at    TIMESTAMPTZ,
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_reservation_time CHECK (end_at > start_at),
    -- 30분 단위 (RV-04). KST는 UTC+9 정시 차이라 UTC 기준으로 검사해도 같아요
    CONSTRAINT ck_reservation_slot CHECK (
        EXTRACT(EPOCH FROM start_at)::BIGINT % 1800 = 0 AND EXTRACT(EPOCH FROM end_at)::BIGINT % 1800 = 0),
    CONSTRAINT ck_reservation_headcount CHECK (headcount > 0),
    CONSTRAINT ck_reservation_status CHECK (
        status IN ('RESERVED', 'IN_USE', 'RETURNED', 'COMPLETED', 'CANCELLED', 'NO_SHOW')),
    -- 상태와 기록 컬럼이 어긋나지 않게 (docs/예약-상태-설계.md)
    CONSTRAINT ck_reservation_state_columns CHECK (
        -- 키 예약은 키 지급으로, 개방 기간 예약은 체크인으로만 IN_USE가 돼요
        (key_required OR (key_issued_at IS NULL AND returned_at IS NULL)) AND
        (NOT key_required OR (checked_in_at IS NULL AND completed_at IS NULL)) AND
        (status <> 'IN_USE'    OR key_issued_at IS NOT NULL OR checked_in_at IS NOT NULL) AND
        (status <> 'RETURNED'  OR (key_issued_at IS NOT NULL AND returned_at IS NOT NULL AND returned_late IS NOT NULL)) AND
        (status <> 'COMPLETED' OR (checked_in_at IS NOT NULL AND completed_at IS NOT NULL)) AND
        (status <> 'CANCELLED' OR cancelled_at IS NOT NULL) AND
        (status <> 'NO_SHOW'   OR no_show_at IS NOT NULL)),
    -- 같은 방 시간 겹침 금지: 동시에 신청해도 1건만 성공해요 (RV-03)
    CONSTRAINT ex_reservation_room_time EXCLUDE USING gist (
        room_id WITH =, tstzrange(start_at, end_at, '[)') WITH &&)
        WHERE (status IN ('RESERVED', 'IN_USE')),
    -- 한 학생이 같은 시간에 방 두 개를 잡지 못하게 (하루 한도가 늘어나도 유지)
    CONSTRAINT ex_reservation_user_time EXCLUDE USING gist (
        user_id WITH =, tstzrange(start_at, end_at, '[)') WITH &&)
        WHERE (status IN ('RESERVED', 'IN_USE'))
);

CREATE INDEX ix_reservation_start ON reservation (start_at);
CREATE INDEX ix_reservation_user_start ON reservation (user_id, start_at DESC);
CREATE INDEX ix_reservation_noshow ON reservation (start_at) WHERE status = 'RESERVED';
CREATE INDEX ix_reservation_autocomplete ON reservation (end_at) WHERE status = 'IN_USE' AND NOT key_required;

-- ─────────────────────────────────────────────
-- 키 지급·반납 이력 (KY-05). 추가만 하고 수정·삭제하지 않아요
-- ─────────────────────────────────────────────
CREATE TABLE key_log (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reservation_id    BIGINT       NOT NULL REFERENCES reservation (id),
    action            VARCHAR(10)  NOT NULL,
    admin_id          BIGINT       NOT NULL REFERENCES users (id),
    identity_verified BOOLEAN      NOT NULL,
    late              BOOLEAN      NOT NULL DEFAULT FALSE,
    memo              VARCHAR(200),
    occurred_at       TIMESTAMPTZ  NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_key_log_action CHECK (action IN ('ISSUE', 'RETURN')),
    -- 실물 학생증 확인 없이는 키를 지급할 수 없어요 (KY-01)
    CONSTRAINT ck_key_log_issue_verified CHECK (action <> 'ISSUE' OR identity_verified),
    -- 같은 예약에 지급·반납이 두 번 기록되지 않게 (동시 클릭 방지)
    CONSTRAINT uq_key_log_reservation_action UNIQUE (reservation_id, action)
);

CREATE INDEX ix_key_log_occurred ON key_log (occurred_at DESC);

CREATE FUNCTION forbid_key_log_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'key_log는 수정·삭제할 수 없어요 (KY-05)';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_key_log_immutable
    BEFORE UPDATE OR DELETE ON key_log
    FOR EACH ROW EXECUTE FUNCTION forbid_key_log_change();

-- ─────────────────────────────────────────────
-- 운영 정책 (PO-05, AD-06). 항상 1행이에요
-- 운영 시간은 0시 기준 분이에요. 마감 24:00(1440)을 Java LocalTime으로 표현할 수 없어서요
-- ─────────────────────────────────────────────
CREATE TABLE operation_policy (
    id                       SMALLINT PRIMARY KEY,
    default_open_minute      SMALLINT    NOT NULL,
    default_close_minute     SMALLINT    NOT NULL,
    max_duration_minutes     SMALLINT    NOT NULL,
    daily_limit              SMALLINT    NOT NULL,
    booking_window_days      SMALLINT    NOT NULL,
    key_issue_before_minutes SMALLINT    NOT NULL,
    no_show_grace_minutes    SMALLINT    NOT NULL,
    penalty_window_days      SMALLINT    NOT NULL,
    penalty_threshold        SMALLINT    NOT NULL,
    penalty_days             SMALLINT    NOT NULL,
    updated_at               TIMESTAMPTZ NOT NULL,
    updated_by               BIGINT REFERENCES users (id),
    CONSTRAINT ck_operation_policy_single CHECK (id = 1),
    CONSTRAINT ck_operation_policy_hours CHECK (
        default_open_minute >= 0 AND default_close_minute <= 1440
        AND default_open_minute < default_close_minute
        AND default_open_minute % 30 = 0 AND default_close_minute % 30 = 0),
    CONSTRAINT ck_operation_policy_values CHECK (
        max_duration_minutes > 0 AND max_duration_minutes % 30 = 0
        AND daily_limit > 0 AND booking_window_days >= 0
        AND key_issue_before_minutes >= 0 AND no_show_grace_minutes > 0
        AND penalty_window_days > 0 AND penalty_threshold > 0 AND penalty_days > 0)
);

-- ─────────────────────────────────────────────
-- 특별 운영 기간·휴관일·개방 기간 (PO-01~03, OP-01)
-- ─────────────────────────────────────────────
CREATE TABLE operation_period (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name         VARCHAR(50) NOT NULL,
    start_date   DATE        NOT NULL,
    end_date     DATE        NOT NULL,
    closed       BOOLEAN     NOT NULL DEFAULT FALSE,
    key_required BOOLEAN     NOT NULL DEFAULT TRUE,
    open_minute  SMALLINT,
    close_minute SMALLINT,
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_operation_period_dates CHECK (end_date >= start_date),
    CONSTRAINT ck_operation_period_hours CHECK (
        (closed AND open_minute IS NULL AND close_minute IS NULL)
        OR (NOT closed AND open_minute IS NOT NULL AND close_minute IS NOT NULL
            AND open_minute >= 0 AND close_minute <= 1440 AND open_minute < close_minute
            AND open_minute % 30 = 0 AND close_minute % 30 = 0)),
    -- 기간끼리 겹치면 그날 운영 시간을 정할 수 없어요
    CONSTRAINT ex_operation_period_dates EXCLUDE USING gist (
        daterange(start_date, end_date, '[]') WITH &&)
);

-- ─────────────────────────────────────────────
-- 이용 제한 (NS-02, NS-03)
-- ─────────────────────────────────────────────
CREATE TABLE user_restriction (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users (id),
    start_at    TIMESTAMPTZ  NOT NULL,
    end_at      TIMESTAMPTZ  NOT NULL,
    reason      VARCHAR(200) NOT NULL,
    lifted_at   TIMESTAMPTZ,
    lifted_by   BIGINT REFERENCES users (id),
    lift_reason VARCHAR(200),
    created_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_user_restriction_time CHECK (end_at > start_at),
    CONSTRAINT ck_user_restriction_lift CHECK (
        (lifted_at IS NULL AND lifted_by IS NULL AND lift_reason IS NULL)
        OR (lifted_at IS NOT NULL AND lifted_by IS NOT NULL AND lift_reason IS NOT NULL)),
    -- 해제되지 않은 제한끼리 겹치지 않게 → 스케줄러가 여러 번 돌아도 제한이 중복되지 않아요 (NS-02)
    CONSTRAINT ex_user_restriction_active EXCLUDE USING gist (
        user_id WITH =, tstzrange(start_at, end_at, '[)') WITH &&)
        WHERE (lifted_at IS NULL)
);

-- ─────────────────────────────────────────────
-- 정책 변경 이력 (AD-06), 관리자 조치 기록 (AD-08). 둘 다 추가만 해요
-- ─────────────────────────────────────────────
CREATE TABLE policy_change_log (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    target       VARCHAR(20) NOT NULL,
    target_id    BIGINT,
    changed_by   BIGINT      NOT NULL REFERENCES users (id),
    before_value JSONB,
    after_value  JSONB,
    created_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_policy_change_log_target CHECK (target IN ('POLICY', 'PERIOD'))
);

CREATE TABLE admin_audit_log (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_id    BIGINT       NOT NULL REFERENCES users (id),
    action      VARCHAR(30)  NOT NULL,
    target_type VARCHAR(30)  NOT NULL,
    target_id   BIGINT,
    reason      VARCHAR(200),
    created_at  TIMESTAMPTZ  NOT NULL
);

-- ─────────────────────────────────────────────
-- 초기 데이터: 방(CLAUDE.md 2장), 운영 정책(D-4, D-5 확정값)
-- 관리자 계정은 비밀번호가 필요해서 마이그레이션에 넣지 않아요 (scripts/seed-demo.mjs·운영 절차로 만들어요)
-- ─────────────────────────────────────────────
INSERT INTO room (name, capacity, sort_order, created_at, updated_at) VALUES
    ('1번방', 4, 1, now(), now()),
    ('2번방', 4, 2, now(), now()),
    ('3번방', 4, 3, now(), now()),
    ('4번방', 4, 4, now(), now()),
    ('5번방', 8, 5, now(), now());

INSERT INTO operation_policy (
    id, default_open_minute, default_close_minute, max_duration_minutes, daily_limit, booking_window_days,
    key_issue_before_minutes, no_show_grace_minutes, penalty_window_days, penalty_threshold, penalty_days, updated_at)
VALUES (1, 540, 1080, 180, 1, 7, 10, 15, 14, 2, 7, now());
