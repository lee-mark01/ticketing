-- =============================================
-- V2__seed_data.sql — 시드 데이터 (중규모)
-- 공연장 3개, 좌석 ~500개, 공연 5개
-- =============================================

-- 1. 공연장
INSERT INTO venues (name, address) VALUES
    ('올림픽홀', '서울시 송파구 올림픽로 424'),
    ('블루스퀘어', '서울시 용산구 이태원로 294'),
    ('세종문화회관', '서울시 종로구 세종대로 175');

-- 2. 좌석 생성 (generate_series로 일괄 생성)

-- 올림픽홀 (venue_id=1): A/B/C 구역, 5열 x 12좌석 = 180석
INSERT INTO seats (venue_id, section, seat_row, seat_number)
SELECT 1, sec, chr(64 + r), CAST(n AS VARCHAR)
FROM (VALUES ('A'), ('B'), ('C')) AS sections(sec),
     generate_series(1, 5) AS r,
     generate_series(1, 12) AS n;

-- 블루스퀘어 (venue_id=2): A/B 구역, 4열 x 10좌석 = 80석
INSERT INTO seats (venue_id, section, seat_row, seat_number)
SELECT 2, sec, chr(64 + r), CAST(n AS VARCHAR)
FROM (VALUES ('A'), ('B')) AS sections(sec),
     generate_series(1, 4) AS r,
     generate_series(1, 10) AS n;

-- 세종문화회관 (venue_id=3): A/B/C/D 구역, 6열 x 15좌석 = 360석
INSERT INTO seats (venue_id, section, seat_row, seat_number)
SELECT 3, sec, chr(64 + r), CAST(n AS VARCHAR)
FROM (VALUES ('A'), ('B'), ('C'), ('D')) AS sections(sec),
     generate_series(1, 6) AS r,
     generate_series(1, 15) AS n;

-- 3. 공연 (5개)
INSERT INTO events (venue_id, title, starts_at, booking_opens_at, available_seat_count) VALUES
    (1, '2026 Summer Jazz Night',     '2026-07-01 19:00', '2026-06-15 10:00', 180),
    (1, 'Classical Music Festival',    '2026-07-15 18:00', '2026-06-20 10:00', 180),
    (2, 'Indie Band Showcase',         '2026-08-01 20:00', '2026-07-10 10:00', 80),
    (3, '한여름 밤의 오케스트라',       '2026-08-15 19:30', '2026-07-20 10:00', 360),
    (3, '국악 퓨전 콘서트',            '2026-09-01 18:00', '2026-08-01 10:00', 360);

-- 4. 공연별 좌석 (event_seats) — 구역별 차등 가격

-- Event 1: 올림픽홀 (venue_id=1)
INSERT INTO event_seats (event_id, seat_id, status, price)
SELECT 1, s.id, 'AVAILABLE',
       CASE s.section WHEN 'A' THEN 99000 WHEN 'B' THEN 77000 ELSE 55000 END
FROM seats s WHERE s.venue_id = 1;

-- Event 2: 올림픽홀 (venue_id=1)
INSERT INTO event_seats (event_id, seat_id, status, price)
SELECT 2, s.id, 'AVAILABLE',
       CASE s.section WHEN 'A' THEN 110000 WHEN 'B' THEN 88000 ELSE 66000 END
FROM seats s WHERE s.venue_id = 1;

-- Event 3: 블루스퀘어 (venue_id=2)
INSERT INTO event_seats (event_id, seat_id, status, price)
SELECT 3, s.id, 'AVAILABLE',
       CASE s.section WHEN 'A' THEN 85000 ELSE 65000 END
FROM seats s WHERE s.venue_id = 2;

-- Event 4: 세종문화회관 (venue_id=3)
INSERT INTO event_seats (event_id, seat_id, status, price)
SELECT 4, s.id, 'AVAILABLE',
       CASE s.section WHEN 'A' THEN 120000 WHEN 'B' THEN 95000
                      WHEN 'C' THEN 70000 ELSE 50000 END
FROM seats s WHERE s.venue_id = 3;

-- Event 5: 세종문화회관 (venue_id=3)
INSERT INTO event_seats (event_id, seat_id, status, price)
SELECT 5, s.id, 'AVAILABLE',
       CASE s.section WHEN 'A' THEN 100000 WHEN 'B' THEN 80000
                      WHEN 'C' THEN 60000 ELSE 40000 END
FROM seats s WHERE s.venue_id = 3;
