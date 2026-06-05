# DECISIONS — 설계 결정 로그 (ADR)

> 정규화·인덱스·락 전략 등 중요한 결정을 내릴 때마다 한 항목씩 추가한다.
> 발표 Q&A("왜 이렇게 했나요?")의 근거 자료가 된다.
>
> 형식:
> ## [날짜] 제목
> - 맥락: (어떤 문제/선택지였나)
> - 결정: (무엇을 골랐나)
> - 이유: (왜 / 트레이드오프)

---

## [seed] ORM 전략: JPA + native SQL 하이브리드
- 맥락: DB 과목이라 "쿼리를 직접 잘 짠다"를 보여줘야 하는데 JPA만 쓰면 SQL이 가려짐.
- 결정: JPA는 엔티티 매핑/단순 CRUD에만, 발표용 핵심 쿼리는 native/JdbcTemplate으로 직접 작성. p6spy로 모든 SQL 로깅.
- 이유: 추상화 편의와 쿼리 통제력을 동시에 확보. 생성 SQL을 노출해 "이해하고 쓴다"를 증명.

## [seed] 좌석 모델: seats / event_seats 분리
- 맥락: 좌석 정보를 공연마다 둘지, 물리좌석과 공연별 상태를 나눌지.
- 결정: 물리좌석(`seats`)과 공연별 재고(`event_seats`)를 분리.
- 이유: 좌석 정보 갱신 이상(update anomaly) 방지, 3NF 만족.

## [seed] 동시성: 비관적 락 기본 + 낙관적 락 비교
- 맥락: 같은 좌석 동시 예매를 어떻게 막을지.
- 결정: 예매 확정 경로는 `SELECT ... FOR UPDATE` 기본, 낙관적 락(`@Version`)도 별도 구현해 비교.
- 이유: 좌석은 경합이 강해 비관적 락이 안전. 두 방식을 모두 시연해 동시성 이해도를 보여줌.

## [2026-05-29] 레포 구조: 모노레포
- 맥락: 백엔드(Spring Boot)와 프론트엔드를 같은 레포에 둘지, 분리할지.
- 결정: 모노레포. `backend/`, `frontend/` 디렉토리로 분리.
- 이유: 대학 프로젝트라 제출 링크 하나로 관리. Vercel은 모노레포에서 특정 폴더만 배포 가능.

## [2026-05-29] Status 컬럼 타입: VARCHAR + CHECK 제약
- 맥락: event_seats.status 등 상태 컬럼을 PostgreSQL ENUM으로 할지, VARCHAR + CHECK로 할지.
- 결정: VARCHAR + CHECK 제약.
- 이유: ENUM보다 유연(값 추가 시 ALTER TYPE 불필요). 발표에서 "CHECK 제약으로 데이터 무결성 보장" 설명 포인트.

## [2026-05-29] JWT: stateless, Redis 없이
- 맥락: 인증 토큰 관리에 Redis가 필요한가.
- 결정: JWT stateless. 별도 저장소 없이 서명 검증만.
- 이유: 로그아웃 블랙리스트 불필요(기능 범위 밖). Redis는 SPEC.md에서 명시적 제외.

## [2026-05-30] 엔티티 관계 로딩: 전부 LAZY, @OneToMany 없음
- 맥락: JPA 연관관계의 기본 페치 전략을 정해야 함.
- 결정: 모든 `@ManyToOne`은 `LAZY`. `@OneToMany` 컬렉션은 아예 만들지 않음.
- 이유: N+1 방지. 컬렉션이 있으면 부모 조회 시 자식을 자동으로 끌고 올 위험. 필요한 곳에서 명시적 쿼리로 조회.

## [2026-05-30] 좌석맵 쿼리: native SQL + 인터페이스 프로젝션
- 맥락: 좌석맵 조회를 JPQL로 할지 native SQL로 할지.
- 결정: native SQL + `SeatMapProjection` 인터페이스.
- 이유: 하드규칙 #1(핵심 쿼리는 SQL 직접 작성). Phase 5에서 EXPLAIN ANALYZE 측정 대상. 프로젝션으로 불필요한 엔티티 생성 없이 결과 매핑.

## [2026-05-30] DTO: Java record / 엔티티: class + Lombok
- 맥락: DTO와 엔티티의 구현 방식.
- 결정: DTO는 Java record(불변), 엔티티는 class + Lombok(@Getter, @NoArgsConstructor).
- 이유: JPA는 record를 지원하지 않음(기본 생성자, setter, 프록시 필요). DTO는 불변이 맞으므로 record가 적합.

## [2026-05-30] 환경변수: .env + export, 하드코딩 금지
- 맥락: DB 비밀번호, JWT secret 등 민감 정보 관리.
- 결정: application.yml에서 `${ENV_VAR}` 참조, 실제 값은 `.env` 파일(gitignore)에 `export` 형태로 보관.
- 이유: 비밀번호 하드코딩 습관 방지. `.env.example`로 필요한 변수 안내.

## [2026-06-03] availableSeatCount 갱신 시점: HOLD에서 감소
- 맥락: `events.available_seat_count`를 HOLD 시점에 줄일지, CONFIRM 시점에 줄일지.
- 결정: HOLD 성공 시 감소, CONFIRM 시 변화 없음, CANCEL/EXPIRE 시 증가.
- 이유: availableSeatCount는 "현재 선택 가능한 좌석 수"를 의미. HELD 좌석은 다른 사용자가 선택할 수 없으므로 HOLD 시점에 감소해야 좌석맵 조회 결과가 정확하다.

## [2026-06-03] HOLD 좌석 조회: eventId 조건 포함
- 맥락: HOLD API에서 좌석을 조회할 때 seatIds만으로 조회할지, eventId도 조건에 포함할지.
- 결정: `findAllByEventIdAndIdInWithLock(eventId, seatIds)` — eventId 조건 포함.
- 이유: 다른 공연의 좌석 ID가 섞여 들어왔을 때 조회 결과 수 불일치로 자연스럽게 거부. 별도 검증 로직 없이 쿼리 단에서 방어.

## [2026-06-03] CONFIRM 롤백 범위: HELD 유지, AVAILABLE 아님
- 맥락: CONFIRM 트랜잭션 실패 시 좌석을 AVAILABLE로 돌릴지, HELD로 유지할지.
- 결정: HELD 유지. AVAILABLE 복구는 사용자의 CANCEL 또는 스케줄러의 EXPIRE에서만.
- 이유: HOLD와 CONFIRM은 별도 트랜잭션. CONFIRM 롤백은 CONFIRM 트랜잭션 내 변경만 되돌리므로, 이전 트랜잭션(HOLD)에서 커밋된 HELD 상태는 유지되는 것이 올바른 동작.

## [2026-06-03] 만료 스케줄러: fixedRate 60초
- 맥락: HOLD 만료를 어떻게 감지하고 처리할지.
- 결정: `@Scheduled(fixedRate = 60_000)`으로 1분마다 만료 HOLD 일괄 처리.
- 이유: HOLD 유효시간이 7분이므로 1분 간격이면 최대 1분 지연으로 충분. 실시간성이 중요하지 않고, DB 폴링이 단순해 구현/디버깅이 쉽다.

## [2026-06-03] 운영 기본 락 전략: PESSIMISTIC (FOR UPDATE)
- 맥락: 좌석 HOLD 동시 요청에서 어떤 락 전략을 운영 기본값으로 쓸지.
- 결정: PESSIMISTIC (SELECT ... FOR UPDATE).
- 이유: 좌석 예매는 경합이 강해 비관적 락이 가장 안전. NAIVE는 중복판매, OPTIMISTIC은 충돌 시 재시도 정책이 필요. deterministic 테스트와 k6 부하 테스트로 3가지 전략을 비교하되, 운영은 PESSIMISTIC 유지.

## [2026-06-03] 동시성 실험 코드: 운영과 완전 분리
- 맥락: NAIVE(unsafe) 구현을 어디에 둘지.
- 결정: `ConcurrencyExperimentService` + `ExperimentController`(`experiment` profile 전용)로 분리. NAIVE는 JPA @Version을 우회하여 JdbcTemplate으로 직접 UPDATE.
- 이유: 운영 `ReservationService`에 unsafe 코드가 혼입되면 안 됨. experiment profile이 아닌 환경에서는 실험 endpoint가 빈으로 등록되지 않아 접근 불가.

## [2026-06-03] 동시성 테스트 방법론: deterministic + realistic 분리
- 맥락: 동시성 테스트를 어떻게 설계할지.
- 결정: CyclicBarrier/CountDownLatch로 critical interleaving을 재현하는 deterministic test + k6로 barrier 없이 HTTP 부하를 보내는 realistic load test로 분리.
- 이유: startLatch만으로는 한 스레드가 먼저 끝나서 동시성 문제 재현이 안 될 수 있음. readBarrier로 모든 스레드가 조회 완료 후 동시에 UPDATE로 진입해야 critical interleaving 결정적 재현 가능. k6는 성능 합격 기준이 아니라 전략별 trade-off 비교 자료.

## [2026-06-03] oversell 판정 기준: active reservation_item 중복 여부
- 맥락: 중복판매를 어떻게 판정할지.
- 결정: "성공 요청 수 > 1"이 아니라, 같은 event_seat_id에 active(PENDING/CONFIRMED) reservation_item이 2건 이상인지로 판정.
- 이유: 성공 수만 보면 서로 다른 좌석에 대한 성공과 구별 못 함. DB 상태 기반 판정이 더 정확.

## [2026-06-03] 다중 좌석 묶음 HOLD 테스트 (자동 배정 대신)
- 맥락: "재고 N개, 동시 M명 → 정확히 N건 판매" 테스트를 어떻게 할지.
- 결정: 현재 API가 사용자가 특정 seatIds를 선택하는 구조이므로, "좌석 3개 묶음을 10명이 동시 HOLD" 테스트로 변경.
- 이유: 자동 배정 API가 없으므로 묶음 HOLD의 전체 성공/전체 실패 보장을 검증하는 것이 적합.

## [2026-06-04] 대량 데이터는 Flyway 아닌 별도 seed script로 분리
- 맥락: Phase 5 측정용 대량 데이터(100,000건)를 어떻게 넣을지.
- 결정: `scripts/seed-large-data.sql`로 분리. V2는 기본 데모 데이터로 유지.
- 이유: 측정용 데이터를 Flyway에 넣으면 운영/배포 환경에도 적용됨. 측정과 스키마 마이그레이션은 분리.

## [2026-06-04] 인덱스는 핫패스 쿼리 기준 최소 3개
- 맥락: 어떤 인덱스를 추가할지.
- 결정: `event_seats(event_id, status)`, `reservations(status, expires_at)`, `reservations(user_id, created_at DESC)` 3개.
- 이유: EXPLAIN ANALYZE 결과 각각 85%, 84%, 24% 성능 개선 확인. HOLD FOR UPDATE는 PK로 충분(negative control).

## [2026-06-04] available_seat_count는 COUNT 회피용 캐시 컬럼 유지
- 맥락: 잔여 좌석 수를 매번 COUNT할지, 캐시 컬럼으로 관리할지.
- 결정: 비정규화 캐시 컬럼 유지. HOLD/CANCEL/EXPIRE 시 증감.
- 이유: event_seats 100,000건 기준 COUNT는 비용이 큼. 캐시 컬럼으로 O(1) 조회. trade-off: HOLD/CANCEL 시 추가 UPDATE 1회.

## [2026-06-05] 배포 방식: Neon + Render + Vercel
- 맥락: Vercel(HTTPS)에서 백엔드 호출 시 mixed content 문제. 백엔드도 HTTPS 필요. Koyeb은 Pro 결제 필요하여 제외.
- 결정: Neon(DB) + Render Free Web Service(Spring Boot) + Vercel(Next.js). HTTPS 자동.
- 이유: Render 무료 티어는 15분 미사용 시 spin down → 첫 접속 30~60초 지연 발생. 발표/시연 전 워밍업 필요. CORS는 FRONTEND_URL 환경변수 기반. experiment endpoint는 prod에서 비활성.
