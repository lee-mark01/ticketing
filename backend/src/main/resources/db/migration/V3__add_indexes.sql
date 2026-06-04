-- =============================================
-- V3__add_indexes.sql — 핫패스 쿼리 인덱스 추가
-- Phase 5: EXPLAIN ANALYZE before/after 비교용
-- =============================================

-- 좌석맵 조회 + AVAILABLE 좌석 필터
-- 대상: GET /api/events/{eventId}/seats
-- 쿼리: WHERE event_id = ? AND status = 'AVAILABLE'
-- 효과: event_id로 필터 후 status로 추가 필터 (2,000건 → 100건)
CREATE INDEX idx_event_seats_event_status ON event_seats (event_id, status);

-- 만료 HOLD 스케줄러
-- 대상: HoldExpiryScheduler (1분마다 실행)
-- 쿼리: WHERE status = 'PENDING' AND expires_at < NOW()
-- 효과: PENDING만 먼저 필터 후 expires_at range scan
CREATE INDEX idx_reservations_status_expires ON reservations (status, expires_at);

-- 내 예매 목록 조회
-- 대상: GET /api/me/reservations
-- 쿼리: WHERE user_id = ? ORDER BY created_at DESC LIMIT 20
-- 효과: user_id 필터 + created_at 역순 정렬을 인덱스로 커버
CREATE INDEX idx_reservations_user_created ON reservations (user_id, created_at DESC);
