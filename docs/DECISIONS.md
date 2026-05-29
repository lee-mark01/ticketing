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

## [미결] 배포 방식 & HTTPS
- 맥락: Vercel(HTTPS)에서 백엔드 호출 시 mixed content 문제. 백엔드도 HTTPS 필요.
- 선택지: (1) PaaS(Koyeb/Render) — HTTPS 자동, nginx 불필요 (2) EC2 + nginx + Let's Encrypt
- 결정: Phase 6에서 확정. 지금은 로컬 HTTP로 개발.
