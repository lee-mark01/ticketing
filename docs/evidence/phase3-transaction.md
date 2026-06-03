# Phase 3 — 트랜잭션 원자성 증거물

## 1. JUnit 테스트 결과 (7/7 통과)

```
ReservationServiceTest
  ✅ HOLD 성공: 좌석 AVAILABLE→HELD, reservation PENDING, items 생성, availableSeatCount 감소
  ✅ HOLD 실패: 이미 점유된 좌석 포함 시 전체 실패, 일부만 HELD 안 됨, availableSeatCount 변화 없음
  ✅ CONFIRM 성공: reservation CONFIRMED, 좌석 SOLD, payment 생성, availableSeatCount 변화 없음
  ✅ CONFIRM 실패(롤백): seat.sell() 이후 예외 시 reservation PENDING, 좌석 HELD 유지, payment 미생성, availableSeatCount 변화 없음
  ✅ 만료된 HOLD 결제 실패: expires_at이 지난 reservation은 confirm 불가
  ✅ 만료 스케줄러: PENDING+expired → EXPIRED 처리, HELD→AVAILABLE, availableSeatCount 증가
  ✅ 사용자 소유권: 다른 사용자의 reservation은 cancel/confirm 불가
```

테스트 환경: Testcontainers + PostgreSQL 16, `@SpringBootTest` 통합 테스트.

---

## 2. 트랜잭션 흐름별 DB 상태 변화

### 정상 흐름: HOLD → CONFIRM

| 단계 | event_seats.status | reservations.status | payments | available_seat_count |
|------|--------------------|---------------------|----------|----------------------|
| 초기 | AVAILABLE | - | - | N |
| HOLD 후 | **HELD** | **PENDING** | - | **N-1** |
| CONFIRM 후 | **SOLD** | **CONFIRMED** | **PAID** | N-1 (변화 없음) |

### 실패 흐름: HOLD → CONFIRM 실패 (RuntimeException)

| 단계 | event_seats.status | reservations.status | payments | available_seat_count |
|------|--------------------|---------------------|----------|----------------------|
| 초기 | AVAILABLE | - | - | N |
| HOLD 후 | HELD | PENDING | - | N-1 |
| CONFIRM 실패 후 | **HELD** (롤백) | **PENDING** (롤백) | **없음** (롤백) | **N-1** (변화 없음) |

핵심: HOLD와 CONFIRM은 별도 트랜잭션이므로, CONFIRM 롤백 시 좌석은 AVAILABLE이 아니라 HELD로 유지된다.

### 만료 흐름: HOLD → 시간 경과 → 스케줄러 만료 처리

| 단계 | event_seats.status | reservations.status | available_seat_count |
|------|--------------------|---------------------|----------------------|
| HOLD 후 | HELD | PENDING | N-1 |
| 스케줄러 처리 후 | **AVAILABLE** | **EXPIRED** | **N** (복원) |

---

## 3. 핵심 SQL (p6spy 로그 기준)

### HOLD 시 비관적 락 쿼리
```sql
SELECT es.id, es.event_id, es.seat_id, es.status, es.price, es.version
FROM event_seats es
WHERE es.event_id = ? AND es.id IN (?, ?)
FOR UPDATE
```
`FOR UPDATE`가 붙어 다른 트랜잭션이 같은 좌석 행을 수정할 수 없다.

### CONFIRM 시 좌석 상태 변경
```sql
UPDATE event_seats SET status = 'SOLD', version = version + 1
WHERE id = ? AND version = ?
```

### 결제 생성
```sql
INSERT INTO payments (reservation_id, amount, status, paid_at)
VALUES (?, ?, 'PAID', ?)
```

### 예매 확정
```sql
UPDATE reservations SET status = 'CONFIRMED' WHERE id = ?
```

위 4개 쿼리가 하나의 트랜잭션 안에서 실행되며, 중간 실패 시 전체 롤백된다.

---

## 4. 롤백 테스트 예외 주입 지점

```
confirmReservation() 내부 실행 순서:
  1. reservation 조회 + 만료/상태 검증
  2. reservationItems 조회
  3. 각 seat.sell()          ← HELD → SOLD 변경 (메모리)
  4. ★ beforePaymentHook     ← 여기서 RuntimeException 발생
  5. Payment.create()        ← 이 단계 도달 못 함
  6. reservation.confirm()   ← 이 단계 도달 못 함
  → @Transactional 롤백: 3번의 SOLD도 HELD로 복원
```

`AopTestUtils.getTargetObject()`로 프록시 뒤 실제 빈에 훅을 주입하여 테스트.

---

## 5. availableSeatCount 정합성 규칙

| 이벤트 | availableSeatCount 변화 | 이유 |
|--------|-------------------------|------|
| HOLD 성공 | **감소** | 선택 불가 상태가 됨 |
| CONFIRM 성공 | 변화 없음 | 이미 HOLD에서 감소됨 |
| CONFIRM 실패 | 변화 없음 | 트랜잭션 롤백 |
| CANCEL | **증가** | 다시 선택 가능 |
| EXPIRE (스케줄러) | **증가** | 다시 선택 가능 |
