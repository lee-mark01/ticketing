-- =============================================
-- V1__init.sql — 전체 스키마 초기 생성
-- 테이블 순서: FK 의존성 고려 (독립 → 참조)
-- =============================================

-- 1. users (독립)
CREATE TABLE users (
    id         BIGSERIAL    PRIMARY KEY,
    email      VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    name       VARCHAR(100) NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT now()
);

-- 2. venues (독립)
CREATE TABLE venues (
    id      BIGSERIAL    PRIMARY KEY,
    name    VARCHAR(200) NOT NULL,
    address VARCHAR(500) NOT NULL
);

-- 3. seats (venues 참조) — 공연장의 물리 좌석
CREATE TABLE seats (
    id          BIGSERIAL    PRIMARY KEY,
    venue_id    BIGINT       NOT NULL REFERENCES venues(id),
    section     VARCHAR(50)  NOT NULL,
    seat_row    VARCHAR(10)  NOT NULL,
    seat_number VARCHAR(10)  NOT NULL,
    UNIQUE (venue_id, section, seat_row, seat_number)
);

-- 4. events (venues 참조)
CREATE TABLE events (
    id                   BIGSERIAL    PRIMARY KEY,
    venue_id             BIGINT       NOT NULL REFERENCES venues(id),
    title                VARCHAR(300) NOT NULL,
    starts_at            TIMESTAMP    NOT NULL,
    booking_opens_at     TIMESTAMP    NOT NULL,
    available_seat_count INT          NOT NULL DEFAULT 0
);

-- 5. event_seats (events + seats 참조) — 공연별 좌석 재고 (핵심 테이블)
--    status: VARCHAR + CHECK 제약으로 데이터 무결성 보장
--    version: Phase 4 낙관적 락(@Version)용
CREATE TABLE event_seats (
    id       BIGSERIAL      PRIMARY KEY,
    event_id BIGINT         NOT NULL REFERENCES events(id),
    seat_id  BIGINT         NOT NULL REFERENCES seats(id),
    status   VARCHAR(20)    NOT NULL DEFAULT 'AVAILABLE'
             CHECK (status IN ('AVAILABLE', 'HELD', 'SOLD')),
    price    INT            NOT NULL,
    version  INT            NOT NULL DEFAULT 0,
    UNIQUE (event_id, seat_id)
);

-- 6. reservations (users + events 참조)
CREATE TABLE reservations (
    id         BIGSERIAL    PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users(id),
    event_id   BIGINT       NOT NULL REFERENCES events(id),
    status     VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
               CHECK (status IN ('PENDING', 'CONFIRMED', 'EXPIRED', 'CANCELLED')),
    expires_at TIMESTAMP,
    created_at TIMESTAMP    NOT NULL DEFAULT now()
);

-- 7. reservation_items (reservations + event_seats 참조) — 예매-좌석 연결
CREATE TABLE reservation_items (
    id              BIGSERIAL PRIMARY KEY,
    reservation_id  BIGINT    NOT NULL REFERENCES reservations(id),
    event_seat_id   BIGINT    NOT NULL REFERENCES event_seats(id),
    UNIQUE (reservation_id, event_seat_id)
);

-- 8. payments (reservations 참조)
CREATE TABLE payments (
    id             BIGSERIAL    PRIMARY KEY,
    reservation_id BIGINT       NOT NULL UNIQUE REFERENCES reservations(id),
    amount         INT          NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                   CHECK (status IN ('PENDING', 'PAID', 'FAILED', 'REFUNDED')),
    paid_at        TIMESTAMP
);
