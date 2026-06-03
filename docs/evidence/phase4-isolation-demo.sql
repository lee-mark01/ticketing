-- =============================================
-- Phase 4 — 격리 수준 시연: READ COMMITTED vs SERIALIZABLE
-- available_seat_count read-modify-write로 lost update 시연
-- =============================================

-- 사전 준비: 좌석 수 확인
-- SELECT available_seat_count FROM events WHERE id = 1;  → 100 (또는 현재 값)

-- =============================================
-- 시나리오 1: READ COMMITTED — lost update 발생
-- =============================================

-- Session A:
BEGIN;
SET TRANSACTION ISOLATION LEVEL READ COMMITTED;
SELECT available_seat_count FROM events WHERE id = 1;
-- → 100

-- Session B:
BEGIN;
SET TRANSACTION ISOLATION LEVEL READ COMMITTED;
SELECT available_seat_count FROM events WHERE id = 1;
-- → 100 (아직 A가 커밋 전이므로 같은 값)

-- Session A:
UPDATE events SET available_seat_count = 99 WHERE id = 1;
-- → 성공

-- Session B:
UPDATE events SET available_seat_count = 99 WHERE id = 1;
-- → A의 row lock 때문에 대기...

-- Session A:
COMMIT;
-- → B의 UPDATE 실행됨 (대기 해제)

-- Session B:
COMMIT;

-- 결과 확인:
SELECT available_seat_count FROM events WHERE id = 1;
-- → 99  ← 2건 감소해야 하는데 1건만 감소 (lost update!)
-- A가 100→99, B도 100→99로 저장. B가 A의 변경을 덮어씀.

-- =============================================
-- 시나리오 2: SERIALIZABLE — 충돌 감지
-- =============================================

-- 먼저 초기화:
UPDATE events SET available_seat_count = 100 WHERE id = 1;

-- Session A:
BEGIN;
SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;
SELECT available_seat_count FROM events WHERE id = 1;
-- → 100

-- Session B:
BEGIN;
SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;
SELECT available_seat_count FROM events WHERE id = 1;
-- → 100

-- Session A:
UPDATE events SET available_seat_count = 99 WHERE id = 1;
-- → 성공

-- Session B:
UPDATE events SET available_seat_count = 99 WHERE id = 1;
-- → A의 row lock 때문에 대기...

-- Session A:
COMMIT;

-- Session B:
-- → ERROR: could not serialize access due to concurrent update
-- → 트랜잭션 자동 ABORT, 재시도 필요

-- Session B:
ROLLBACK;

-- 결과 확인:
SELECT available_seat_count FROM events WHERE id = 1;
-- → 99  ← A의 변경만 반영. B는 serialization failure로 롤백.
-- 정합성 유지! 재시도하면 B는 99를 읽고 98로 변경하게 됨.

-- =============================================
-- 결론
-- =============================================
-- READ COMMITTED: 동시 read-modify-write에서 lost update 발생 가능.
--   → 해결: SELECT ... FOR UPDATE (비관적 락) 또는 @Version (낙관적 락)
-- SERIALIZABLE: DB가 충돌을 감지하고 하나를 실패시킴.
--   → 안전하지만 throughput 감소. 실패한 트랜잭션은 재시도 필요.
