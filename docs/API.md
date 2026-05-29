# API — 엔드포인트 명세

> Phase 2~3 구현 시 이 문서를 기준으로 컨트롤러/DTO를 만든다.
> ✅ = MVP 필수, ◻ = 선택(여유 시).

## 공통 규약
- Base URL: `/api`
- 요청/응답 본문은 JSON. 시각은 ISO-8601 문자열 (`2026-06-01T19:00:00+09:00`).
- 인증: 로그인 후 발급한 토큰을 `Authorization: Bearer <token>` 헤더로 전달.
  (JWT 가정. 세션 방식으로 바꿔도 무방.)
- 페이징 응답 공통 형태:
  ```json
  { "content": [ ... ], "page": 0, "size": 20, "totalElements": 137, "totalPages": 7 }
  ```
- 에러 응답 공통 형태:
  ```json
  { "timestamp": "...", "status": 409, "code": "SEAT_NOT_AVAILABLE", "message": "이미 선점된 좌석입니다." }
  ```
- 주요 상태코드: 200 성공 / 201 생성 / 204 본문없음 / 400 검증실패 / 401 미인증 /
  403 권한없음 / 404 없음 / 409 충돌(동시성·상태) / 402 결제실패(모의).

---

## 1. 인증 (Auth)

### ✅ POST /api/auth/signup — 회원가입
- 인증: 불필요
- Request body: `{ "email": "a@b.com", "password": "...", "name": "홍길동" }`
- 201: `{ "id": 1, "email": "a@b.com", "name": "홍길동" }`
- 에러: 400(검증), 409(이메일 중복 — `EMAIL_DUPLICATED`)

### ✅ POST /api/auth/login — 로그인
- 인증: 불필요
- Request body: `{ "email": "a@b.com", "password": "..." }`
- 200: `{ "accessToken": "...", "tokenType": "Bearer", "expiresIn": 3600 }`
- 에러: 401(자격증명 불일치 — `INVALID_CREDENTIALS`)

### ◻ POST /api/auth/logout — 로그아웃
- 인증: 필요 / 204

### ◻ GET /api/me — 내 정보
- 인증: 필요
- 200: `{ "id": 1, "email": "a@b.com", "name": "홍길동" }`
- 에러: 401

---

## 2. 공연 조회 (Events)

### ✅ GET /api/events — 공연 목록
- 인증: 불필요
- Query: `page`(기본 0), `size`(기본 20), `q`(제목 검색, 선택), `sort`(선택)
- 200 (페이징):
  ```json
  { "content": [
      { "id": 10, "title": "...", "venueName": "올림픽홀",
        "startsAt": "...", "bookingOpensAt": "...", "availableSeatCount": 320 }
    ], "page": 0, "size": 20, "totalElements": 42, "totalPages": 3 }
  ```

### ✅ GET /api/events/{eventId} — 공연 상세
- 인증: 불필요
- 200:
  ```json
  { "id": 10, "title": "...", "startsAt": "...", "bookingOpensAt": "...",
    "venue": { "id": 3, "name": "올림픽홀", "address": "..." },
    "availableSeatCount": 320 }
  ```
- 에러: 404(`EVENT_NOT_FOUND`)

### ✅ GET /api/events/{eventId}/seats — 좌석맵 / 잔여좌석 조회  ★인덱싱 핫패스
- 인증: 불필요
- Query: `section`(구역 필터, 선택), `status`(선택, 기본 전체)
- 200:
  ```json
  { "eventId": 10, "seats": [
      { "eventSeatId": 5001, "section": "A", "row": "3", "number": "12",
        "status": "AVAILABLE", "price": 99000 }
    ] }
  ```
- 에러: 404
- 구현 메모: 이 쿼리를 native SQL로 작성하고 `event_seats(event_id, status)` 인덱스
  추가 전/후 `EXPLAIN ANALYZE`를 측정해 `docs/evidence/`에 저장.

---

## 3. 예매 (Booking) — 프로젝트의 심장

### ✅ POST /api/events/{eventId}/holds — 좌석 HOLD  ★동시성(락) 핵심
- 인증: 필요
- 설명: 선택한 좌석들을 임시 점유. 예매건(PENDING) 생성 + `event_seats` AVAILABLE→HELD +
  `expiresAt` 설정. **같은 좌석 동시 요청 시 한 건만 성공.**
- Request body: `{ "seatIds": [5001, 5002] }`  (값은 event_seat의 id)
- 201:
  ```json
  { "reservationId": 7001, "status": "PENDING",
    "heldSeats": [ { "eventSeatId": 5001, "price": 99000 } ],
    "totalAmount": 198000, "expiresAt": "2026-06-01T19:07:00+09:00" }
  ```
- 에러:
  - 409 `SEAT_NOT_AVAILABLE` — 이미 다른 사람이 선점/판매됨 ← **동시성 데모의 결과**
  - 400 `INVALID_SEATS`, 404 `EVENT_NOT_FOUND`, 401
- 구현 메모: read-modify-write 구간에 비관적 락(`SELECT ... FOR UPDATE`) 또는
  낙관적 락(`@Version`) 적용. 동시성 테스트/부하테스트는 이 엔드포인트를 동시 호출.

### ✅ DELETE /api/reservations/{reservationId}/hold — HOLD 취소
- 인증: 필요 (본인 예매만)
- 설명: 점유 해제. `event_seats` HELD→AVAILABLE, 예매건 CANCELLED.
- 204 (본문 없음)
- 에러: 403(`FORBIDDEN`), 404, 409(`ALREADY_CONFIRMED` — 확정된 건은 이 API로 취소 불가)

### ✅ POST /api/reservations/{reservationId}/confirm — 결제 확정  ★트랜잭션(원자성) 핵심
- 인증: 필요 (본인 예매만)
- 설명: 결제(모의) 후 확정. **하나의 트랜잭션**으로 예매건 PENDING→CONFIRMED +
  `event_seats` HELD→SOLD + `payment` 생성. 일부 실패 시 전체 롤백.
- Request body: `{ "paymentMethod": "CARD" }`  (모의 결제)
- 200:
  ```json
  { "reservationId": 7001, "status": "CONFIRMED",
    "payment": { "id": 9001, "amount": 198000, "status": "PAID", "paidAt": "..." } }
  ```
- 에러:
  - 409 `RESERVATION_EXPIRED` — HOLD가 만료됨 / `ALREADY_CONFIRMED`
  - 402 `PAYMENT_FAILED` — 모의 결제 실패(롤백 시연용으로 일부러 실패시킬 수 있음)
  - 403, 404, 401

### ✅ GET /api/me/reservations — 내 예매 목록
- 인증: 필요
- Query: `status`(필터, 선택), `page`, `size`
- 200 (페이징):
  ```json
  { "content": [
      { "reservationId": 7001, "eventTitle": "...", "status": "CONFIRMED",
        "seats": ["A-3-12"], "totalAmount": 198000, "createdAt": "..." }
    ], "page": 0, "size": 20, "totalElements": 3, "totalPages": 1 }
  ```

### ◻ GET /api/reservations/{reservationId} — 예매 상세
- 인증: 필요 (본인) / 200 상세 / 403, 404

### ◻ POST /api/reservations/{reservationId}/cancel — 확정 후 취소/환불
- 인증: 필요 (본인)
- 200: `{ "reservationId": 7001, "status": "CANCELLED", "refund": { "amount": 198000, "status": "REFUNDED" } }`
- 에러: 409(취소 불가 상태), 403, 404

---

## 4. 관리/데이터 준비 (Admin) — ◻ 선택, 시드 SQL 권장
| 엔드포인트 | 설명 |
|---|---|
| `POST /api/admin/venues` | 공연장 등록 |
| `POST /api/admin/venues/{id}/seats` | 좌석 일괄 등록 |
| `POST /api/admin/events` | 공연 등록 (+ event_seats 생성) |

> 발표 주제가 아니므로 API 대신 Flyway 시드/스크립트로 데이터를 넣는 것을 권장.

---

## 5. API가 아닌 것
- **만료 HOLD 자동 해제**: `@Scheduled` 백그라운드 작업. `expiresAt` 지난 PENDING 예매를
  찾아 좌석을 AVAILABLE로 되돌리고 예매건을 EXPIRED 처리. (엔드포인트 아님)

---

## 엔드포인트 ↔ DB 개념 빠른 매핑
| 개념 | 주 엔드포인트 |
|---|---|
| 인덱싱 | `GET /events/{id}/seats` |
| 트랜잭션 | `POST /reservations/{id}/confirm` |
| 동시성 컨트롤 | `POST /events/{id}/holds` |
| 설계/정규화 | 전체 스키마 (DATA_MODEL.md) |
