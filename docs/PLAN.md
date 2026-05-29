# PLAN — 단계별 개발 계획 (작업 contract)

> 작업은 이 순서대로 진행한다. 한 단계의 "완료 기준"을 모두 만족해야 다음 단계로 넘어간다.
> 단계 완료 시 git 커밋(메시지에 `[Phase N]` 포함).

**[현재 작업] → Phase 1**

---

## Phase 1 — 설계 & 프로젝트 세팅
목표: 뼈대와 스키마 기반을 잡는다.
- [ ] Spring Boot 프로젝트 생성 (Gradle, Java 21, 위 의존성)
- [ ] `docker-compose.yml`로 로컬 PostgreSQL 기동
- [ ] Flyway 연결, `V1__init.sql`에 DATA_MODEL.md의 전체 스키마 작성
- [ ] p6spy 설정 (실행 SQL 콘솔 출력 확인)
완료 기준: `./gradlew bootRun`이 뜨고, Flyway가 스키마를 생성하며, p6spy 로그가 찍힌다.

## Phase 2 — 기본 도메인 & 조회
목표: 엔티티와 읽기 기능.
- [ ] JPA 엔티티 매핑 (users, venues, seats, events, event_seats ...)
- [ ] 회원가입/로그인 (Spring Security)
- [ ] 공연 목록 / 공연 상세 / 좌석맵(잔여좌석) 조회 — **잔여좌석 쿼리는 native SQL로 작성**
- [ ] 시드 데이터 (공연장·좌석·공연) 삽입 스크립트
완료 기준: 좌석맵 화면에서 특정 공연의 좌석 상태가 보인다.

## Phase 3 — 예매 핵심 로직 (트랜잭션)
목표: HOLD → 결제 → SOLD 흐름.
- [ ] 좌석 HOLD API (status AVAILABLE→HELD, reservation PENDING 생성, expires_at 설정)
- [ ] 결제 확정 API: `reservation→CONFIRMED` + `event_seats→SOLD` + `payment 생성`을
      **하나의 트랜잭션**으로 (`@Transactional`). 중간 실패 시 전체 롤백 확인.
- [ ] 만료 HOLD 자동 해제 스케줄러
완료 기준: 결제 실패를 일부러 던졌을 때 좌석이 AVAILABLE로 돌아오는 것을 테스트로 증명.

## Phase 4 — 동시성 컨트롤 (★ 핵심)
목표: 같은 좌석 동시 요청 안전 처리 + 증명.
- [ ] 락 없는 naive 버전으로 **중복판매 재현** (테스트로 oversell 발생 확인)
- [ ] 비관적 락 버전: `SELECT ... FOR UPDATE`
- [ ] 낙관적 락 버전: `@Version` + 충돌 시 재시도
- [ ] 격리 수준 시연: READ COMMITTED의 lost update → SERIALIZABLE 비교
- [ ] **락 없으면 실패 / 있으면 통과**하는 멀티스레드 통합 테스트
완료 기준: 동시 N요청에서 좌석 1개가 정확히 1건만 판매됨을 테스트로 증명.

## Phase 5 — 인덱싱 & 측정
목표: 발표용 증거 데이터 수집.
- [ ] 대량 시드 데이터(좌석/예매 수만 건) 삽입
- [ ] 핫패스 쿼리 `EXPLAIN ANALYZE` 측정 (인덱스 추가 전)
- [ ] `V2__add_indexes.sql`로 인덱스 추가 후 재측정
- [ ] before/after 결과를 `docs/evidence/`에 저장
- [ ] k6로 예매 오픈 부하 테스트, 결과 표/그래프 저장
완료 기준: 인덱스 전후 비교표와 부하테스트 결과가 파일로 남는다.

## Phase 6 — 배포 & 산출물
목표: 제출.
- [ ] 배포 방식 확정 (PaaS vs EC2+nginx). HTTPS 필수 — `DECISIONS.md [미결]` 참고.
- [ ] DB + 앱 배포, 라이브 URL 확보
- [ ] README 정리 (소개, ERD, 실행법, 라이브 URL, 동시성 설명)
- [ ] PPT 초안 생성 (CONCEPTS.md 증거물 기반)
완료 기준: 외부에서 접속 가능한 URL + README + PPT.
