# 발표 구조 초안 — TicketBook

> 최종 PPT 제작 전 발표 흐름 정리용. 12장 슬라이드.
> 각 슬라이드: 제목 / 핵심 메시지 / bullet / 증거 파일 / 발표자 포인트.

---

## 슬라이드 1: 프로젝트 개요

**핵심 메시지:** 인기 공연 예매 오픈 시 같은 좌석에 수백 명이 동시 요청하면 DB에서 어떤 문제가 발생하는가?

- 티켓팅 서비스에서 DB 문제가 중요한 이유
- 좌석 1개에 동시 요청 → 중복판매(oversell) 위험
- 5개 DB 개념으로 문제를 해결하고 증명

**증거:** 없음 (도입부)
**발표자 포인트:** "예매 오픈 시 수초 내에 수백 건이 같은 좌석에 몰린다. 이 상황을 DB 수준에서 어떻게 안전하게 처리할지가 이 프로젝트의 핵심이다."

---

## 슬라이드 2: ERD / 데이터 모델

**핵심 메시지:** seats(물리좌석)와 event_seats(공연별 재고)를 분리한 3NF 설계.

- 8개 테이블 ERD 다이어그램
- seats/event_seats 분리 → 갱신 이상 방지 (3NF)
- available_seat_count는 의도적 비정규화 (COUNT 회피용 캐시)
- reservation_items 교차 테이블로 M:N 해소

**증거:** `docs/DATA_MODEL.md`, `V1__init.sql`
**발표자 포인트:** "같은 좌석이 공연마다 다른 가격을 가진다. seats에 가격을 넣으면 공연마다 UPDATE해야 하는 갱신 이상이 발생한다. 그래서 분리했다."

---

## 슬라이드 3: 예매 흐름 — HOLD → CONFIRM → EXPIRE

**핵심 메시지:** 좌석 선점(HOLD) → 결제 확정(CONFIRM) → 만료 자동 해제(EXPIRE)의 3단계 흐름.

- HOLD: AVAILABLE → HELD + reservation PENDING + expires_at 7분
- CONFIRM: HELD → SOLD + reservation CONFIRMED + payment PAID (단일 트랜잭션)
- EXPIRE: 스케줄러가 1분마다 만료 HOLD 해제
- availableSeatCount: HOLD에서 감소, CONFIRM에서 변화 없음, CANCEL/EXPIRE에서 증가

**증거:** `docs/evidence/phase3-transaction.md` — DB 상태 변화 표
**발표자 포인트:** "HOLD와 CONFIRM은 별도 트랜잭션이다. CONFIRM이 실패해도 좌석은 HELD로 유지된다. AVAILABLE 복구는 CANCEL이나 EXPIRE에서만 일어난다."

---

## 슬라이드 4: 트랜잭션 롤백 증명

**핵심 메시지:** CONFIRM 중간에 예외가 발생하면 좌석 SOLD, payment 생성, reservation 확정이 모두 롤백된다.

- seat.sell() 이후 / Payment.create() 직전에 RuntimeException 강제 발생
- 결과: 좌석 HELD 유지, reservation PENDING 유지, payment 미생성
- `@Transactional` 어노테이션이 보장하는 원자성
- 7개 테스트 전체 통과

**증거:** `docs/evidence/phase3-transaction.md` — 롤백 테스트 결과, JUnit @DisplayName 목록
**발표자 포인트:** "AopTestUtils로 프록시 뒤 실제 빈에 훅을 주입하여 정확한 지점에서 예외를 발생시켰다. 트랜잭션이 없으면 좌석만 SOLD가 되고 결제는 안 된 부분 업데이트 상태가 된다."

---

## 슬라이드 5: 동시성 문제 — 같은 좌석 동시 요청

**핵심 메시지:** 보호 없이 같은 좌석에 동시 요청하면 중복판매(oversell)가 발생한다.

- Thread A: SELECT(AVAILABLE) → UPDATE(HELD)
- Thread B: SELECT(AVAILABLE) → UPDATE(HELD)
- 둘 다 성공 → 같은 좌석에 2개 예매 = oversell
- 불변조건 위반: 같은 event_seat_id에 active reservation_item ≤ 1

**증거:** `docs/evidence/phase4-concurrency.md` — NAIVE 테스트 결과
**발표자 포인트:** "NAIVE 구현은 JPA @Version을 우회하여 version 조건 없이 직접 UPDATE한다. 이것이 왜 위험한지를 deterministic 테스트로 보여준다."

---

## 슬라이드 6: NAIVE vs PESSIMISTIC vs OPTIMISTIC 비교

**핵심 메시지:** 3가지 락 전략의 동작 원리와 결과 차이.

| 전략 | 방식 | 결과 |
|------|------|------|
| NAIVE | version 없는 UPDATE | oversell 발생 |
| PESSIMISTIC | SELECT ... FOR UPDATE | 1건 성공, 나머지 lock wait 후 실패 |
| OPTIMISTIC | @Version WHERE version=0 | 1건 성공, 나머지 version conflict |

- PESSIMISTIC: 안전하지만 lock wait 시간 존재
- OPTIMISTIC: 안전하지만 충돌 시 재시도 정책 필요
- 운영 기본 전략: PESSIMISTIC (경합이 강한 좌석 예매에 적합)

**증거:** `docs/evidence/phase4-concurrency.md` — 비교표, p6spy SQL 로그
**발표자 포인트:** "FOR UPDATE는 첫 번째 트랜잭션이 끝날 때까지 나머지를 대기시킨다. @Version은 대기 없이 먼저 커밋한 쪽만 성공한다. trade-off가 다르다."

---

## 슬라이드 7: Deterministic Concurrency Test 결과

**핵심 메시지:** barrier로 critical interleaving을 결정적으로 재현하여 4가지 시나리오를 증명했다.

- NAIVE: 10스레드 → active reservation_item ≥ 2 (oversell 재현)
- PESSIMISTIC: 10스레드 → 성공 정확히 1건, active item 1건
- OPTIMISTIC: 10스레드 → 성공 정확히 1건, version conflict 확인
- PESSIMISTIC 3좌석 묶음: 10스레드 → 성공 1건, items 3건

> "이 테스트는 실제 운영 부하 테스트가 아니라, 동시성 문제가 발생 가능한 critical interleaving을 결정적으로 재현하기 위한 correctness test이다."

**증거:** `ConcurrencyTest.java` — 4개 테스트 @DisplayName + 결과
**발표자 포인트:** "readBarrier로 모든 스레드가 AVAILABLE을 읽은 뒤 동시에 UPDATE로 진입하게 했다. PESSIMISTIC에서는 barrier 사용 불가 — FOR UPDATE에서 다른 스레드가 대기하므로 barrier가 채워지지 않는다."

---

## 슬라이드 8: 인덱싱 — 핫패스 쿼리와 인덱스 설계

**핵심 메시지:** 호출 빈도가 높은 3개 쿼리에 복합 인덱스를 추가하여 읽기 성능을 개선했다.

- `event_seats(event_id, status)` — 좌석맵 + AVAILABLE 필터
- `reservations(status, expires_at)` — 만료 스케줄러
- `reservations(user_id, created_at DESC)` — 내 예매 목록
- HOLD FOR UPDATE는 PK로 충분 (negative control)

**증거:** `V3__add_indexes.sql`, `docs/DECISIONS.md`
**발표자 포인트:** "인덱스를 무작정 추가하지 않았다. EXPLAIN으로 실제 필요한 쿼리만 선정하고, PK로 충분한 쿼리는 제외했다."

---

## 슬라이드 9: EXPLAIN ANALYZE Before/After

**핵심 메시지:** 인덱스 추가 후 AVAILABLE 조회 85%, 만료 HOLD 조회 84% 성능 개선.

| 쿼리 | Before | After | 개선 | 핵심 변화 |
|------|--------|-------|------|----------|
| AVAILABLE 좌석 | 1.890ms | 0.286ms | 85% | Filter → Index Cond |
| 만료 HOLD | 33.880ms | 5.341ms | 84% | Seq Scan → Index Scan |
| 내 예매 목록 | 61.904ms | 47.089ms | 24% | reservations Seq Scan 제거 |
| HOLD FOR UPDATE | 0.668ms | 0.286ms | — | PK 충분 (negative control) |

- 데이터 규모: event_seats 101,160건, reservations 100,000건

**증거:** `docs/evidence/phase5-indexing.md`, `docs/evidence/phase5/before-*.txt, after-*.txt`
**발표자 포인트:** "좌석맵 전체 조회는 기존 UNIQUE 인덱스로 이미 커버되어 개선폭이 작았다. 복합 인덱스의 두 번째 컬럼(status)은 AVAILABLE 필터 쿼리에서 효과가 드러난다."

---

## 슬라이드 10: 배포 구조

**핵심 메시지:** Neon(DB) + Render(API) + Vercel(Frontend), HTTPS 자동.

- Neon: 무료 PostgreSQL, Flyway V1~V4 자동 적용
- Render: Spring Boot Docker, prod profile (experiment endpoint 비활성). 무료 티어 spin down 주의.
- Vercel: Next.js, NEXT_PUBLIC_API_URL로 백엔드 연결
- 라이브 URL: (배포 후 기재)

**증거:** `Dockerfile`, `application.yml`
**발표자 포인트:** "experiment profile의 unsafe 코드는 배포 환경에서 절대 열리지 않는다. prod profile에서는 @Profile('experiment') 빈이 등록되지 않는다."

---

## 슬라이드 11: 결과 요약

**핵심 메시지:** 5개 DB 개념을 코드와 테스트로 증명했다.

| 개념 | 증명 |
|------|------|
| 설계 | 8개 테이블 ERD, FK 관계, CHECK 제약 |
| 정규화 | seats/event_seats 분리 (3NF), available_seat_count 비정규화 trade-off |
| 인덱싱 | EXPLAIN before/after (85%↑, 84%↑), negative control |
| 트랜잭션 | CONFIRM 롤백 테스트 7개 (JUnit) |
| 동시성 | NAIVE oversell 재현 + PESSIMISTIC/OPTIMISTIC 정합성 테스트 4개 |

- 전체 테스트: 11개 (Phase 3: 7개 + Phase 4: 4개)

**증거:** `docs/CONCEPTS.md`
**발표자 포인트:** "기능 완성이 아니라 DB 개념 증명이 목표였다. 각 개념마다 테스트로 증명하고, 증거물을 docs/evidence/에 정리했다."

---

## 슬라이드 12: 한계와 개선 방향

**핵심 메시지:** 증명한 것과 아직 남은 것을 구분한다.

**증명 완료:**
- Deterministic concurrency test로 3전략 비교 (correctness)
- EXPLAIN ANALYZE before/after 인덱스 효과
- 트랜잭션 롤백 원자성

**아직 실행 예정 / 미완:**
- k6 부하 테스트: 스크립트 작성 완료, 실제 실행 결과는 미수집
- 격리 수준 시연: SQL 스크립트 작성 완료, 수동 시연 예정
- 분산 락(Redis): 단일 서버 구조에서는 불필요, 멀티 서버 시 필요

**개선 방향:**
- OPTIMISTIC 충돌 시 자동 재시도 정책
- 모니터링/알림 (Grafana, Prometheus)
- 프론트엔드 실시간 좌석 상태 업데이트 (WebSocket/SSE)

**증거:** 없음 (마무리)
**발표자 포인트:** "k6 스크립트는 완성했지만 실행 결과는 아직 수집하지 않았다. 과장하지 않고 실제 증명한 것만 발표한다."
