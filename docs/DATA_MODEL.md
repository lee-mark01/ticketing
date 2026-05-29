# DATA_MODEL — 데이터 모델 / ERD / 정규화

> 실제 스키마는 Flyway 마이그레이션(`src/main/resources/db/migration/`)이 단일 진실 공급원이다.
> 이 문서는 설계 의도와 근거를 설명한다.

## 1. 엔티티 개요

| 테이블 | 설명 | 주요 컬럼 |
|---|---|---|
| `users` | 회원 | id, email(unique), password_hash, name, created_at |
| `venues` | 공연장 | id, name, address |
| `seats` | 공연장의 **물리 좌석** | id, venue_id(FK), section, seat_row, seat_number |
| `events` | 공연 | id, venue_id(FK), title, starts_at, booking_opens_at, available_seat_count(캐시) |
| `event_seats` | **공연별 좌석 재고 (핵심)** | id, event_id(FK), seat_id(FK), status, price, version |
| `reservations` | 예매 건 | id, user_id(FK), event_id(FK), status, expires_at, created_at |
| `reservation_items` | 예매-좌석 연결 | id, reservation_id(FK), event_seat_id(FK) |
| `payments` | 결제 기록 | id, reservation_id(FK), amount, status, paid_at |

`event_seats.status`: `AVAILABLE` / `HELD` / `SOLD`
`reservations.status`: `PENDING` / `CONFIRMED` / `EXPIRED` / `CANCELLED`

## 2. 관계
- venue 1 — N seat
- venue 1 — N event
- event 1 — N event_seat, seat 1 — N event_seat (event_seat = event×seat 교차)
- user 1 — N reservation
- reservation 1 — N reservation_item — 1 event_seat
- reservation 1 — 1 payment

## 3. 정규화 근거 (발표 포인트)
- **`seats`와 `event_seats`를 분리한 이유**: 좌석의 물리 정보(구역/열/번호)는 공연장에 종속된
  고정 정보다. 이를 공연마다 복제하면 좌석 정보 변경 시 여러 행을 고쳐야 하는 갱신 이상(update
  anomaly)이 생긴다. 물리 좌석(`seats`)과 "그 좌석이 이 공연에서 어떤 상태/가격인가"(`event_seats`)를
  분리해 3NF를 만족시킨다.
- **`reservation_items` 교차 테이블**: 한 예매가 여러 좌석을 가질 수 있는 다대다 관계를 정규화로 해소.
- **의도적 비정규화**: `events.available_seat_count`는 매번 COUNT 쿼리를 피하기 위한 캐시 컬럼.
  정규화 위반임을 인지하고 읽기 성능을 위해 선택한 트레이드오프다. 좌석 상태 변경 트랜잭션 안에서
  함께 갱신해 정합성을 유지한다. (발표에서 "정규화를 알지만 일부러 깼다"로 설명)

## 4. 인덱스 계획 (before/after 측정 대상)
- `event_seats(event_id, status)` — "이 공연의 잔여좌석 조회" 핫패스. 복합 인덱스.
- `event_seats` 부분 인덱스: `WHERE status = 'AVAILABLE'` — 예매 가능 좌석만 빠르게.
- `reservations(expires_at)` — 만료된 HOLD 정리 배치용.
- `users(email)` — 로그인 조회 (unique 제약과 함께).
- 인덱스는 별도 마이그레이션(`V2__...`)으로 추가해, 추가 전/후 `EXPLAIN ANALYZE` 비교를 남긴다.

## 5. 동시성 관련 설계
- `event_seats.version`: JPA `@Version` 낙관적 락용.
- 예매 확정 경로에서는 `SELECT ... FOR UPDATE`(비관적 락) 적용 — 같은 좌석 동시 요청 직렬화.
- 두 전략을 모두 구현해 비교하는 것이 동시성 시연의 핵심.
