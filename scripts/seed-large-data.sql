-- =============================================
-- Phase 5 — 대량 데이터 시드 (측정 전용)
-- V1+V2 적용 후 실행. Flyway가 아닌 별도 스크립트.
--
-- 목표:
--   event_seats    100,000건
--   reservations   100,000건
--   reservation_items 100,000건 (1:1)
--   payments        60,000건
--   users            5,000명
--
-- 실행: psql -h localhost -U <user> -d <db> -f scripts/seed-large-data.sql
-- =============================================

BEGIN;

-- =============================================
-- 1. 측정용 사용자 5,000명
-- =============================================
INSERT INTO users (email, password_hash, name, created_at)
SELECT
    'bench' || g || '@test.com',
    'hashed',
    '측정사용자' || g,
    NOW() - (random() * interval '90 days')
FROM generate_series(1, 5000) AS g
ON CONFLICT (email) DO NOTHING;

-- heavy user 확인용 (user_id는 아래에서 출력)
-- bench1@test.com 을 heavy user로 사용

-- =============================================
-- 2. 측정용 venue + physical seats 2,000개
-- =============================================
INSERT INTO venues (name, address)
VALUES ('측정용공연장', '서울시 강남구 테헤란로 1');
-- venue_id = 마지막 삽입된 ID

-- seats: A~T 구역(20개) × 10열 × 10좌석 = 2,000개
INSERT INTO seats (venue_id, section, seat_row, seat_number)
SELECT
    (SELECT MAX(id) FROM venues),
    chr(64 + sec),                    -- A, B, C, ... T
    chr(64 + r),                      -- A, B, C, ... J (열)
    CAST(n AS VARCHAR)                -- 1~10 (좌석번호)
FROM generate_series(1, 20) AS sec,
     generate_series(1, 10) AS r,
     generate_series(1, 10) AS n;

-- =============================================
-- 3. 측정용 events 50개
-- =============================================
INSERT INTO events (venue_id, title, starts_at, booking_opens_at, available_seat_count)
SELECT
    (SELECT MAX(id) FROM venues),
    '측정공연 #' || g,
    '2026-09-01'::timestamp + (g * interval '1 day'),
    '2026-08-01'::timestamp + (g * interval '1 day'),
    2000
FROM generate_series(1, 50) AS g;

-- =============================================
-- 4. event_seats 100,000건 (50 events × 2,000 seats)
-- =============================================
INSERT INTO event_seats (event_id, seat_id, status, price, version)
SELECT
    e.id,
    s.id,
    'AVAILABLE',
    (CASE (s.id % 4)
        WHEN 0 THEN 120000
        WHEN 1 THEN 99000
        WHEN 2 THEN 77000
        ELSE 55000
    END),
    0
FROM events e
JOIN seats s ON s.venue_id = e.venue_id
WHERE e.title LIKE '측정공연%';

-- =============================================
-- 5. Hot event: 마지막 측정 이벤트의 좌석 상태 변경
--    2,000석 중 AVAILABLE 100개, HELD 900개, SOLD 1,000개
-- =============================================

-- hot_event_id 확인
DO $$
DECLARE
    hot_event_id BIGINT;
BEGIN
    SELECT MAX(id) INTO hot_event_id FROM events WHERE title LIKE '측정공연%';
    RAISE NOTICE 'Hot event ID: %', hot_event_id;

    -- SOLD 1,000개 (id 기준 처음 1,000개)
    UPDATE event_seats SET status = 'SOLD', version = 1
    WHERE event_id = hot_event_id
      AND id IN (
          SELECT id FROM event_seats
          WHERE event_id = hot_event_id
          ORDER BY id LIMIT 1000
      );

    -- HELD 900개 (다음 900개)
    UPDATE event_seats SET status = 'HELD', version = 1
    WHERE event_id = hot_event_id
      AND id IN (
          SELECT id FROM event_seats
          WHERE event_id = hot_event_id AND status = 'AVAILABLE'
          ORDER BY id LIMIT 900
      );

    -- 나머지 100개는 AVAILABLE 유지
END $$;

-- hot event의 available_seat_count 갱신
UPDATE events SET available_seat_count = 100
WHERE id = (SELECT MAX(id) FROM events WHERE title LIKE '측정공연%');

-- =============================================
-- 6. reservations 100,000건
--    status 분포: PENDING 20%, CONFIRMED 60%, EXPIRED 15%, CANCELLED 5%
--    각 reservation은 측정용 event의 event_seat 1개와 연결
-- =============================================

-- heavy user (bench1) 에게 5,000건 배정, 나머지 95,000건은 랜덤 배분

-- 먼저 heavy user ID 확인
DO $$
DECLARE
    heavy_user_id BIGINT;
BEGIN
    SELECT id INTO heavy_user_id FROM users WHERE email = 'bench1@test.com';
    RAISE NOTICE 'Heavy user ID: %', heavy_user_id;
END $$;

-- 임시 시퀀스용 테이블
CREATE TEMP TABLE _res_seq AS
SELECT
    g AS seq_num,
    CASE
        WHEN g <= 5000  THEN (SELECT id FROM users WHERE email = 'bench1@test.com')
        ELSE (SELECT MIN(id) FROM users WHERE email LIKE 'bench%') + ((g - 1) % 5000)
    END AS user_id,
    CASE
        WHEN g % 100 < 20 THEN 'PENDING'
        WHEN g % 100 < 80 THEN 'CONFIRMED'
        WHEN g % 100 < 95 THEN 'EXPIRED'
        ELSE 'CANCELLED'
    END AS status
FROM generate_series(1, 100000) AS g;

-- event_seats를 순서대로 배정 (각 event의 좌석을 순차 사용)
-- 측정용 event_seat ID 범위 확인
CREATE TEMP TABLE _event_seat_ids AS
SELECT id AS es_id, event_id,
       ROW_NUMBER() OVER (ORDER BY id) AS rn
FROM event_seats
WHERE event_id IN (SELECT id FROM events WHERE title LIKE '측정공연%')
ORDER BY id;

-- reservations 삽입
INSERT INTO reservations (user_id, event_id, status, expires_at, created_at)
SELECT
    rs.user_id,
    esi.event_id,
    rs.status,
    CASE
        WHEN rs.status = 'PENDING' AND rs.seq_num % 10 = 0
            THEN NOW() - interval '1 hour'   -- expired PENDING (~2,000건)
        WHEN rs.status = 'PENDING'
            THEN NOW() + interval '7 minutes' -- active PENDING
        ELSE NULL
    END,
    NOW() - ((100000 - rs.seq_num) * interval '1 minute')
FROM _res_seq rs
JOIN _event_seat_ids esi ON esi.rn = rs.seq_num;

-- =============================================
-- 7. reservation_items 100,000건 (1 reservation = 1 item)
-- =============================================
INSERT INTO reservation_items (reservation_id, event_seat_id)
SELECT r.id, esi.es_id
FROM (
    SELECT id, ROW_NUMBER() OVER (ORDER BY id) AS rn
    FROM reservations
    WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'bench%')
    ORDER BY id
) r
JOIN _event_seat_ids esi ON esi.rn = r.rn;

-- =============================================
-- 8. event_seats status 정합성 맞추기
--    PENDING reservation → HELD
--    CONFIRMED reservation → SOLD
--    EXPIRED/CANCELLED → AVAILABLE (이미 해제)
-- =============================================
UPDATE event_seats es SET status = 'HELD', version = 1
FROM reservation_items ri
JOIN reservations r ON r.id = ri.reservation_id
WHERE ri.event_seat_id = es.id
  AND r.status = 'PENDING';

UPDATE event_seats es SET status = 'SOLD', version = 1
FROM reservation_items ri
JOIN reservations r ON r.id = ri.reservation_id
WHERE ri.event_seat_id = es.id
  AND r.status = 'CONFIRMED';

-- EXPIRED/CANCELLED는 이미 AVAILABLE — 변경 불필요

-- =============================================
-- 9. payments 60,000건 (CONFIRMED reservations)
-- =============================================
INSERT INTO payments (reservation_id, amount, status, paid_at)
SELECT r.id,
       es.price,
       'PAID',
       r.created_at + interval '3 minutes'
FROM reservations r
JOIN reservation_items ri ON ri.reservation_id = r.id
JOIN event_seats es ON es.id = ri.event_seat_id
WHERE r.status = 'CONFIRMED';

-- =============================================
-- 10. events.available_seat_count 갱신
-- =============================================
UPDATE events e SET available_seat_count = (
    SELECT COUNT(*) FROM event_seats es
    WHERE es.event_id = e.id AND es.status = 'AVAILABLE'
)
WHERE e.title LIKE '측정공연%';

-- 임시 테이블 정리
DROP TABLE IF EXISTS _res_seq;
DROP TABLE IF EXISTS _event_seat_ids;

COMMIT;

-- =============================================
-- 검증: row count + 핵심 ID 출력
-- =============================================
SELECT '=== Row Count ===' AS info;
SELECT 'users' AS tbl, COUNT(*) AS cnt FROM users
UNION ALL SELECT 'events', COUNT(*) FROM events
UNION ALL SELECT 'seats', COUNT(*) FROM seats
UNION ALL SELECT 'event_seats', COUNT(*) FROM event_seats
UNION ALL SELECT 'reservations', COUNT(*) FROM reservations
UNION ALL SELECT 'reservation_items', COUNT(*) FROM reservation_items
UNION ALL SELECT 'payments', COUNT(*) FROM payments
ORDER BY tbl;

SELECT '=== Hot Event ===' AS info;
SELECT id, title, available_seat_count FROM events
WHERE title LIKE '측정공연%' ORDER BY id DESC LIMIT 1;

SELECT '=== Heavy User ===' AS info;
SELECT id, email, (SELECT COUNT(*) FROM reservations WHERE user_id = u.id) AS reservation_count
FROM users u WHERE email = 'bench1@test.com';

SELECT '=== Event Seat Status Distribution (Hot Event) ===' AS info;
SELECT status, COUNT(*)
FROM event_seats
WHERE event_id = (SELECT MAX(id) FROM events WHERE title LIKE '측정공연%')
GROUP BY status ORDER BY status;

SELECT '=== Reservation Status Distribution ===' AS info;
SELECT status, COUNT(*)
FROM reservations
GROUP BY status ORDER BY status;

SELECT '=== Expired PENDING count ===' AS info;
SELECT COUNT(*) AS expired_pending
FROM reservations
WHERE status = 'PENDING' AND expires_at < NOW();

-- ANALYZE 실행 (통계 갱신)
ANALYZE;
