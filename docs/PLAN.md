# PLAN — 단계별 개발 계획 (작업 contract)

> 작업은 이 순서대로 진행한다. 한 단계의 "완료 기준"을 모두 만족해야 다음 단계로 넘어간다.
> 단계 완료 시 git 커밋(메시지에 `[Phase N]` 포함).

**[현재 작업] → Phase 6**

---

## Phase 1 — 설계 & 프로젝트 세팅 ✅
목표: 뼈대와 스키마 기반을 잡는다.
- [x] Spring Boot 프로젝트 생성 (Gradle, Java 17, 의존성)
- [x] `docker-compose.yml`로 로컬 PostgreSQL 기동
- [x] Flyway 연결, `V1__init.sql`에 DATA_MODEL.md의 전체 스키마 작성
- [x] p6spy 설정 (실행 SQL 콘솔 출력 확인)
완료 기준: ~~`./gradlew bootRun`이 뜨고, Flyway가 스키마를 생성하며, p6spy 로그가 찍힌다.~~ ✅

## Phase 2 — 기본 도메인 & 조회 ✅
목표: 엔티티와 읽기 기능.
- [x] JPA 엔티티 매핑 8개 + enum 3개 (모두 LAZY, @OneToMany 없음)
- [x] 회원가입/로그인 (Spring Security + JWT stateless)
- [x] 공연 목록 / 공연 상세 / 좌석맵(잔여좌석) 조회 — **좌석맵은 native SQL**
- [x] 시드 데이터 `V2__seed_data.sql` (공연장 3개, 좌석 620개, 공연 5개)
완료 기준: ~~좌석맵 화면에서 특정 공연의 좌석 상태가 보인다.~~ ✅

## Phase 3 — 예매 핵심 로직 (트랜잭션) ✅
목표: HOLD → 결제 → SOLD 흐름.
- [x] 좌석 HOLD API (status AVAILABLE→HELD, reservation PENDING 생성, expires_at 설정)
- [x] 결제 확정 API: `reservation→CONFIRMED` + `event_seats→SOLD` + `payment 생성`을
      **하나의 트랜잭션**으로 (`@Transactional`). 중간 실패 시 전체 롤백 확인.
- [x] 만료 HOLD 자동 해제 스케줄러
완료 기준: ~~CONFIRM 실패 시 좌석이 HELD로 유지, payment 미생성, availableSeatCount 변화 없음을 7개 테스트로 증명.~~ ✅

## Phase 4 — 동시성 컨트롤 (★ 핵심) ✅
목표: 같은 좌석 동시 요청 안전 처리 + 증명.
- [x] 락 없는 naive 버전으로 **중복판매 재현** (ConcurrencyExperimentService + JdbcTemplate unsafe UPDATE)
- [x] 비관적 락 버전: `SELECT ... FOR UPDATE` (정확히 1건 성공)
- [x] 낙관적 락 버전: `@Version` + 충돌 감지 (정확히 1건 성공)
- [x] 격리 수준 시연: READ COMMITTED lost update → SERIALIZABLE 비교 (SQL 스크립트)
- [x] **락 없으면 실패 / 있으면 통과**하는 멀티스레드 통합 테스트 (deterministic 4개 통과)
- [x] k6 부하 테스트 스크립트 작성 (smoke/hot_seat/seat_pool/spike)
완료 기준: ~~동시 10요청에서 좌석 1개가 정확히 1건만 판매됨을 테스트로 증명. active reservation_item 기준 oversell 판정.~~ ✅

## Phase 5 — 인덱싱 & 측정 ✅
목표: 발표용 증거 데이터 수집.
- [x] 대량 시드 데이터 (event_seats 101,160건, reservations 100,000건)
- [x] 핫패스 쿼리 5개 `EXPLAIN (ANALYZE, BUFFERS)` before 측정
- [x] `V3__add_indexes.sql` 인덱스 3개 추가 후 after 재측정
- [x] before/after 비교표 + EXPLAIN 원문을 `docs/evidence/phase5/`에 저장
- [x] k6 읽기 부하 테스트 스크립트 작성 (`load-test/booking-read-load.js`)
완료 기준: ~~AVAILABLE 조회 85% 개선, 만료 HOLD 84% 개선, Seq Scan → Index Scan 전환 확인.~~ ✅

## Phase 6 — 배포 & 산출물
목표: 제출.
- [x] 배포 방식 확정: Neon(DB) + Koyeb(API) + Vercel(Frontend). HTTPS 자동.
- [x] 백엔드: CORS(FRONTEND_URL 환경변수), Health endpoint, Dockerfile, V4 시연사용자
- [x] 프론트엔드: Next.js MVP 5페이지 (로그인, 공연목록, 좌석맵, 예매확인, 내예매)
- [x] README.md 정리 (소개, 기술스택, 실행법, 동시성 비교, 프로젝트 구조)
- [x] 발표 구조 초안 12장 (`docs/presentation-outline.md`)
- [ ] Neon + Koyeb + Vercel 실제 배포, 라이브 URL 확보
완료 기준: 외부에서 접속 가능한 URL + README + 발표 초안.
