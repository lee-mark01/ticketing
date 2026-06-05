# TicketBook — 공연 티켓 예매 시스템

대학 데이터베이스 과목 프로젝트. DB 5개 핵심 개념(설계, 정규화, 인덱싱, 트랜잭션, 동시성)을 실제 동작과 측정 데이터로 증명한다.

## 기술 스택

| 구분 | 기술 |
|------|------|
| Backend | Java 17, Spring Boot 3.4, Spring Data JPA, Spring Security |
| Frontend | Next.js (React), TypeScript, Tailwind CSS |
| Database | PostgreSQL 16 (로컬 Docker / 배포 Neon) |
| Migration | Flyway |
| SQL 로깅 | p6spy |
| 테스트 | JUnit 5, Testcontainers |
| 부하 테스트 | k6 |
| 배포 | Neon (DB) + Render (API) + Vercel (Frontend) |

## 라이브 URL

| 구분 | URL |
|------|-----|
| Frontend | https://ticketing-wine-three.vercel.app |
| Backend API | https://ticketing-api-caty.onrender.com |
| Health Check | https://ticketing-api-caty.onrender.com/health |

> Render 무료 티어는 15분 미사용 시 spin down됩니다. 첫 접속 시 30~60초 지연될 수 있습니다.

## 핵심 시연 흐름

1. 로그인 → 2. 공연 목록 → 3. 좌석맵(구역별 색상) → 4. 좌석 HOLD → 5. 결제 CONFIRM → 6. 내 예매 확인

## DB 5개 개념 요약

| 개념 | 구현 위치 | 증명 방법 |
|------|----------|----------|
| 설계 | `V1__init.sql` 8개 테이블 | ERD, FK 관계 |
| 정규화 | seats/event_seats 분리 (3NF) | 갱신 이상 방지, 비정규화(available_seat_count) trade-off |
| 인덱싱 | `V3__add_indexes.sql` 3개 인덱스 | EXPLAIN ANALYZE before/after (AVAILABLE 조회 85%↑, 만료 HOLD 84%↑) |
| 트랜잭션 | `ReservationService.confirmReservation()` | CONFIRM 실패 시 전체 롤백 테스트 7개 |
| 동시성 | NAIVE/PESSIMISTIC/OPTIMISTIC 3전략 | 락 없으면 oversell 재현, 있으면 정확히 1건 (deterministic 테스트 4개) |

## 동시성 처리 비교

| 전략 | 방식 | 결과 |
|------|------|------|
| NAIVE (unsafe) | version 조건 없는 UPDATE | 중복판매 발생 (active reservation_item ≥ 2) |
| PESSIMISTIC | SELECT ... FOR UPDATE | 정확히 1건 성공 |
| OPTIMISTIC | @Version WHERE version = 0 | 정확히 1건 성공, 나머지 version conflict |

## 로컬 실행

### 사전 준비
- Java 17, Docker, Node.js 18+

### Backend
```bash
cd backend
docker compose up -d          # PostgreSQL 기동
source .env                    # 환경변수 로드
./gradlew bootRun             # 서버 기동 (http://localhost:8080)
```

### Frontend
```bash
cd frontend
npm install
npm run dev                    # 개발 서버 (http://localhost:3000)
```

### 테스트
```bash
cd backend
./gradlew test                 # 전체 테스트 (11개)
```

### 시연용 계정
- 이메일: `demo@ticketing.com`
- 비밀번호: `password123`

## 프로젝트 구조

```
ticketing/
├── backend/                    # Spring Boot API
│   ├── src/main/resources/db/migration/
│   │   ├── V1__init.sql        # 스키마
│   │   ├── V2__seed_data.sql   # 시드 데이터
│   │   ├── V3__add_indexes.sql # 인덱스
│   │   └── V4__demo_users.sql  # 시연 사용자
│   └── src/main/java/com/ticketing/
│       ├── booking/            # 예매 도메인
│       ├── event/              # 공연 도메인
│       ├── user/               # 사용자/인증
│       └── config/             # 설정 (Security, JWT, CORS)
├── frontend/                   # Next.js 프론트엔드
├── load-test/                  # k6 부하 테스트 스크립트
├── scripts/                    # 측정용 대량 seed
└── docs/                       # 설계 문서 + 증거물
    ├── SPEC.md, PLAN.md, DATA_MODEL.md, API.md
    ├── CONCEPTS.md, DECISIONS.md
    └── evidence/               # Phase 3~5 증거물
```

## 배포

### 구조
```
[Vercel] ─── HTTPS ───> [Render (Spring Boot)] ─── HTTPS ───> [Neon (PostgreSQL)]
 Next.js                  backend/Dockerfile                Flyway V1~V4 자동 적용
```

> **주의:** Render 무료 티어는 15분 미사용 시 서버가 spin down됩니다.
> 첫 접속 시 백엔드 응답이 **30~60초 지연**될 수 있습니다.
> 발표/시연 전에 `/health` 엔드포인트로 미리 워밍업하세요.

### Backend (Render)
- **Dockerfile 위치:** `backend/Dockerfile`
- **Render 설정:** Root Directory = `backend`, Dockerfile path = `./Dockerfile`
- **빌드:** Dockerfile이 multi-stage build로 내부에서 `./gradlew clean bootJar -x test`를 실행하므로 별도 JAR 생성 불필요
- **환경변수:**
  | 변수 | 설명 |
  |------|------|
  | `DB_URL` | Neon 접속 URL (`jdbc:postgresql://...?sslmode=require`) |
  | `DB_USERNAME` | Neon 사용자명 |
  | `DB_PASSWORD` | Neon 비밀번호 |
  | `JWT_SECRET` | JWT 서명 키 (`openssl rand -base64 32`로 생성) |
  | `FRONTEND_URL` | Vercel 프론트 URL (CORS용) |
- **Health check:** `GET /health` → `{"status":"UP"}`
- **Profile:** 기본 prod. experiment endpoint는 비활성.

### Frontend (Vercel)
- **Root Directory:** `frontend`
- **환경변수:** `NEXT_PUBLIC_API_URL` = Render 백엔드 HTTPS URL

### DB (Neon)
- Neon 무료 티어. Flyway V1~V4 자동 적용.
- V2 시드 데이터 (데모용). Phase 5 대량 seed는 로컬 측정 전용.

## 상세 문서

- [프로젝트 명세](docs/SPEC.md)
- [데이터 모델 / ERD](docs/DATA_MODEL.md)
- [API 명세](docs/API.md)
- [설계 결정 로그](docs/DECISIONS.md)
- [5개 개념 매핑](docs/CONCEPTS.md)
- [개발 계획](docs/PLAN.md)
