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

## [미결] 배포 방식 & HTTPS
- 맥락: Vercel(HTTPS)에서 백엔드 호출 시 mixed content 문제. 백엔드도 HTTPS 필요.
- 선택지: (1) PaaS(Koyeb/Render) — HTTPS 자동, nginx 불필요 (2) EC2 + nginx + Let's Encrypt
- 결정: Phase 6에서 확정. 지금은 로컬 HTTP로 개발.
