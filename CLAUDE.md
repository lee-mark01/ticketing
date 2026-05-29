# CLAUDE.md

공연 티켓 예매 시스템. 대학 데이터베이스 과목 프로젝트.
목표는 "기능 완성"이 아니라 **DB 5개 개념(설계·정규화·인덱싱·트랜잭션·동시성)을 증명**하는 것.
핵심 시연 포인트: 인기 공연 예매 오픈(선착순) 시의 좌석 동시성 처리.

## 기술 스택
- Java 17, Spring Boot 3.4.1, Gradle 8.11.1
- Spring Web, Spring Data JPA, Spring Security
- PostgreSQL (로컬은 Docker, 배포는 Neon)
- Flyway (스키마 마이그레이션)
- p6spy (실행 SQL 로깅)
- 테스트: JUnit5, Testcontainers / 부하: k6

## 하드 규칙 (반드시 지킬 것)
1. **JPA는 엔티티 매핑 + 단순 CRUD에만 사용.** 발표에서 보여줄 핵심 쿼리
   (잔여좌석 조회, 락 쿼리, 복잡한 조인)는 native `@Query` 또는 JdbcTemplate으로 **SQL을 직접 작성**한다.
2. **스키마 변경은 무조건 Flyway 마이그레이션 파일로.** 임의 DDL 실행 금지.
   파일명 규칙: `V{번호}__{설명}.sql` (예: `V2__add_event_seat_indexes.sql`).
3. **p6spy 로깅 항상 켠 상태 유지.** 새 쿼리 추가 시 N+1 발생 여부를 점검하고 보고한다.
4. **오버엔지니어링 금지.** 데모에 필요한 만큼만. 불필요한 추상화 계층/디자인패턴 자제.
5. 설계 결정(정규화·인덱스·락 전략)을 내릴 때마다 한두 줄 근거를 `docs/DECISIONS.md`에 기록한다.
6. 동시성·트랜잭션 코드를 만들거나 수정하면, **락이 없으면 실패하고 있으면 통과하는 테스트**를 함께 작성한다.

## 작업 방식
- 작업은 `docs/PLAN.md`의 단계(Phase)를 순서대로 따른다. 현재 단계는 PLAN.md의 `[현재 작업]` 표시를 참고.
- 한 단계를 끝내면 git 커밋(메시지에 Phase 번호 포함). 단계 도중 임의로 다음 단계로 넘어가지 말 것.
- 스키마 설계와 동시성 로직은 **먼저 Plan Mode로 접근 방식을 제안**하고 승인을 받은 뒤 구현한다.
- 구현 직후, 그 선택의 트레이드오프를 3줄 이내로 요약해 달라는 요청에 응한다 (발표 대비).

## 명령어 (backend/ 디렉토리에서 실행)
- 로컬 DB 기동: `cd backend && docker compose up -d`
- 빌드: `cd backend && ./gradlew build`
- 실행: `cd backend && source .env && ./gradlew bootRun`
- 테스트: `cd backend && ./gradlew test`
- 마이그레이션: `cd backend && ./gradlew flywayMigrate`
- 부하 테스트: `k6 run load-test/booking-open.js`

## 상세 문서 (필요할 때 읽을 것)
- 프로젝트 전체 명세: `docs/SPEC.md`
- 데이터 모델 / ERD / 정규화 근거: `docs/DATA_MODEL.md`
- 단계별 개발 계획 (작업 contract): `docs/PLAN.md`
- 5개 개념 → 구현 위치 → 발표 증거물 매핑: `docs/CONCEPTS.md`
- 설계 결정 로그: `docs/DECISIONS.md`
- API 엔드포인트 명세: `docs/API.md`