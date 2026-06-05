# DEPLOYMENT — 배포 가이드

## 구조

```
[Vercel] ─── HTTPS ───> [Render (Spring Boot)] ─── HTTPS ───> [Neon (PostgreSQL)]
 Next.js                  backend/Dockerfile                Flyway V1~V4 자동 적용
```

## DB (Neon)

- Neon 무료 티어 PostgreSQL 16
- Flyway V1~V4 자동 적용 (앱 기동 시)
- V2 시드 데이터 (데모용). Phase 5 대량 seed는 로컬 측정 전용.

## Backend (Render)

- **Dockerfile 위치:** `backend/Dockerfile` (multi-stage build)
- **Render 설정:** Root Directory = `backend`, Runtime = Docker
- **환경변수:**

  | 변수 | 설명 |
  |------|------|
  | `DB_URL` | Neon 접속 URL (`jdbc:postgresql://...?sslmode=require`) |
  | `DB_USERNAME` | Neon 사용자명 |
  | `DB_PASSWORD` | Neon 비밀번호 |
  | `JWT_SECRET` | JWT 서명 키 (`openssl rand -base64 32`로 생성) |
  | `FRONTEND_URL` | Vercel 프론트 URL (CORS용) |

- **Health check:** `GET /health` → `{"status":"UP"}`
- **Profile:** 기본 prod. experiment endpoint는 `@Profile("experiment")`으로 비활성.
- **주의:** 무료 티어는 15분 미사용 시 spin down. 첫 접속 30~60초 지연.

## Frontend (Vercel)

- **Root Directory:** `frontend`
- **환경변수:** `NEXT_PUBLIC_API_URL` = Render 백엔드 HTTPS URL (끝에 `/` 없이)
- GitHub push 시 자동 배포.

## 현재 라이브 URL

| 구분 | URL |
|------|-----|
| Frontend | https://ticketing-wine-three.vercel.app |
| Backend | https://ticketing-api-caty.onrender.com |
