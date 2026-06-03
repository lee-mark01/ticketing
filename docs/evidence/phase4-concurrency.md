# Phase 4 — 동시성 컨트롤 증거물

## 목적
같은 좌석에 대한 동시 요청에서 중복 예매(oversell) 방지를 증명한다.

## 불변조건
**같은 event_seat_id에 대해 active(PENDING/CONFIRMED) reservation_item은 최대 1건이어야 한다.**

검증 SQL:
```sql
SELECT ri.event_seat_id, COUNT(*) AS active_count
FROM reservation_items ri
JOIN reservations r ON r.id = ri.reservation_id
WHERE ri.event_seat_id = :seatId AND r.status IN ('PENDING', 'CONFIRMED')
GROUP BY ri.event_seat_id
HAVING COUNT(*) > 1;
```
결과가 있으면 oversell.

---

## 테스트 종류 구분

### 1. Deterministic concurrency test
CyclicBarrier / CountDownLatch로 critical interleaving을 결정적으로 재현하는 correctness test.
- 실제 운영 부하 테스트가 **아님**
- 동시성 문제가 발생 가능한 최악의 타이밍을 실험실에서 고정해 재현
- NAIVE/OPTIMISTIC: `readBarrier`로 모든 스레드가 조회 완료 후 동시에 UPDATE 진입
- PESSIMISTIC: `startLatch`로 동시 시작, FOR UPDATE가 자연스럽게 직렬화

### 2. Realistic load test (k6)
barrier/hook 없이 실제 HTTP 요청을 보내는 부하 비교 테스트.
- 전략별 trade-off 비교 자료 (성능 합격 기준 아님)
- 성공/실패 수, 응답시간, oversell 여부 수집

---

## Deterministic 테스트 결과 (4/4 통과)

```
ConcurrencyTest
  ✅ NAIVE: 같은 좌석 1개에 10명 동시 HOLD → oversell 발생 (active reservation_item ≥ 2)
  ✅ PESSIMISTIC: 같은 좌석 1개에 10명 동시 HOLD → 정확히 1건 성공, active reservation_item 1건
  ✅ OPTIMISTIC: 같은 좌석 1개에 10명 동시 HOLD → 정확히 1건 성공, version conflict 발생
  ✅ PESSIMISTIC 다중 좌석 묶음: 좌석 3개를 10명 동시 HOLD → 1명 성공, items 3건, 나머지 전체 실패
```

### 결과 비교표

| 전략 | 스레드 | 성공 수 | active items | oversell | 비고 |
|------|--------|---------|--------------|----------|------|
| NAIVE | 10 | ≥2 | ≥2 | **발생** | version 조건 없는 unsafe UPDATE |
| PESSIMISTIC | 10 | 1 | 1 | 없음 | FOR UPDATE로 직렬화 |
| OPTIMISTIC | 10 | 1 | 1 | 없음 | @Version 충돌 → 나머지 실패 |
| PESSIMISTIC (3좌석) | 10 | 1 | 3 | 없음 | 묶음 HOLD 전체 정합성 |

---

## p6spy SQL 로그

### PESSIMISTIC — FOR UPDATE
```sql
SELECT es.id, es.event_id, es.seat_id, es.status, es.price, es.version
FROM event_seats es
WHERE es.event_id = ? AND es.id IN (?)
FOR UPDATE
```
`FOR UPDATE`로 row lock 획득. 다른 트랜잭션은 이 쿼리에서 대기.

### OPTIMISTIC — @Version WHERE 조건
```sql
UPDATE event_seats
SET status = 'HELD', version = 1
WHERE id = ? AND version = 0
```
먼저 커밋한 쪽이 version을 0→1로 올림. 나중 쪽은 `WHERE version = 0` 조건 불일치 → 0 rows affected → `ObjectOptimisticLockingFailureException`.

### NAIVE — version 없는 unsafe UPDATE
```sql
UPDATE event_seats SET status = 'HELD' WHERE id = ?
```
version 조건 없이 직접 UPDATE. 모든 스레드가 성공하여 중복 예매 발생.

---

## 격리 수준 시연

`docs/evidence/phase4-isolation-demo.sql` 참조.

- **READ COMMITTED**: 두 트랜잭션이 `available_seat_count = 100`을 읽고 각각 99로 저장 → 최종 99 (lost update)
- **SERIALIZABLE**: 두 번째 트랜잭션이 `could not serialize access` 에러로 실패 → 정합성 유지

---

## k6 부하 테스트 (스크립트 작성 완료 / 실행 예정)

> 아래는 k6 부하 테스트 스크립트(`load-test/booking-concurrency.js`)의 설계이다.
> 아직 실행하지 않았으며, experiment profile로 서버를 기동한 뒤 별도로 실행할 예정.

### 실행 방법
```bash
# 서버 기동 (experiment profile)
cd backend && source .env && SPRING_PROFILES_ACTIVE=experiment ./gradlew bootRun

# 사용자 시드
curl -X POST "http://localhost:8080/api/experiment/seed-users?count=200"

# 테스트 실행
k6 run -e STRATEGY=PESSIMISTIC -e SCENARIO=smoke load-test/booking-concurrency.js
k6 run -e STRATEGY=NAIVE -e SCENARIO=hot_seat load-test/booking-concurrency.js
```

### 시나리오

| 시나리오 | VUs | duration/iterations | 목적 |
|----------|-----|---------------------|------|
| A. Smoke | 20 | 10s | HTTP 흐름 정상 동작 확인 |
| B. Hot seat | 100 | 100 iterations | 같은 좌석 1개 집중 경쟁, oversell 여부 |
| C. Seat pool | 200 | 60s | 50~100개 좌석 랜덤, 전략별 성공률/응답시간 비교 |
| D. Spike | 0→200→0 | 10s→30s→10s | 순간 트래픽 비교 |

### k6 결과표 (미실행 — 실행 후 채울 것)

| 시나리오 | 전략 | VUs | 성공 | 실패 | 실패율 | avg | p95 | p99 | oversell |
|----------|------|-----|------|------|--------|-----|-----|-----|----------|
| smoke | PESSIMISTIC | 20 | - | - | - | - | - | - | - |
| hot_seat | NAIVE | 100 | - | - | - | - | - | - | - |
| hot_seat | PESSIMISTIC | 100 | - | - | - | - | - | - | - |
| hot_seat | OPTIMISTIC | 100 | - | - | - | - | - | - | - |

> 실행 환경: (로컬 PC 사양, Docker PostgreSQL, JVM 설정 등 기록)
> VUs를 낮춘 경우 그 이유와 변경값을 여기에 기록.

---

## 결론

- **NAIVE**: 빠를 수 있으나 정합성이 깨짐. version 조건 없는 UPDATE로 중복 예매 발생.
- **PESSIMISTIC**: 안전. FOR UPDATE로 row lock을 걸어 직렬화. lock wait로 p95가 증가할 수 있음.
- **OPTIMISTIC**: 정합성 유지. @Version 충돌을 감지하여 중복 방지. 충돌 시 재시도 정책이 필요함.

운영 기본 전략: **PESSIMISTIC** — 좌석 예매는 경합이 강해 비관적 락이 가장 안전.
