# 시연 체크리스트

---

## 배포 환경 시연

> Render 무료 티어는 15분 미사용 시 spin down됩니다.
> 첫 접속 시 백엔드 응답이 **30~60초 지연**될 수 있습니다.

### 워밍업 (발표 5분 전)
```
브라우저에서 백엔드 /health 접속 → {"status":"UP"} 확인될 때까지 대기
```

### 시연 URL
- 프론트: `(Vercel URL — 배포 후 기재)`
- 백엔드: `(Render URL — 배포 후 기재)`
- 시연용 계정: `demo@ticketing.com` / `password123`

시연 흐름은 아래 "시연 흐름 (클릭 순서)" 동일.

---

## 로컬 시연

### 사전 준비

```bash
# 1. DB 기동
cd backend
docker compose up -d

# 2. 백엔드 기동
source .env
export FRONTEND_URL=http://localhost:3000
./gradlew bootRun
# → "Started TicketingApplication" 확인 (약 30~70초)

# 3. 프론트엔드 기동 (새 터미널)
cd frontend
npm install   # 최초 1회
npm run dev
# → http://localhost:3000 확인
```

## 시연용 계정

| 이메일 | 비밀번호 | 용도 |
|--------|----------|------|
| demo@ticketing.com | password123 | 메인 시연 |
| user1@ticketing.com | password123 | 동시 접속 시연용 |
| user2@ticketing.com | password123 | 동시 접속 시연용 |

## 시연 흐름 (클릭 순서)

### 1. 로그인
- 접속: http://localhost:3000/login
- 이메일: `demo@ticketing.com`, 비밀번호: `password123`
- "로그인" 클릭
- **기대:** 공연 목록 페이지(`/`)로 이동

### 2. 공연 목록
- 접속: http://localhost:3000
- **기대:** 공연 5개가 카드 형태로 표시. 각 카드에 제목, 공연장, 일시, 잔여 좌석 수.

### 3. 공연 상세 + 좌석맵
- 아무 공연 카드 클릭 (예: "2026 Summer Jazz Night")
- **기대:** 구역별(A/B/C) 좌석 그리드 표시
  - 초록: AVAILABLE (클릭 가능)
  - 노랑: HELD
  - 빨강: SOLD
- 범례가 상단에 표시

### 4. 좌석 선택 → HOLD
- AVAILABLE(초록) 좌석 1~2개 클릭
- **기대:** 선택된 좌석이 파란색으로 변경, 하단에 "N석 선택 · 금액" 패널 표시
- "좌석 선점" 클릭
- **기대:** 예매 확인 페이지(`/reservations/{id}`)로 이동

### 5. 결제 확정 (CONFIRM)
- "결제 확정" 클릭
- **기대:** 체크마크(✅) + "예매 확정!" + 결제 금액 + "PAID" 표시
- "내 예매 목록 보기" 클릭

### 6. 내 예매 목록
- 접속: http://localhost:3000/me/reservations
- **기대:** 방금 예매한 건이 "CONFIRMED" 상태로 표시. 좌석 번호(예: A-A-1), 금액 확인.

## 문제 발생 시

| 증상 | 원인 | 해결 |
|------|------|------|
| 공연 목록 로딩 안 됨 | 백엔드 미기동 | `./gradlew bootRun` 확인 |
| 로그인 실패 | V4 migration 미적용 | DB 초기화 후 재기동 |
| HOLD 시 401 | JWT 만료 또는 미로그인 | 다시 로그인 |
| HOLD 시 409 | 이미 선점된 좌석 | 다른 좌석 선택 또는 DB 초기화 |
| CORS 에러 | FRONTEND_URL 미설정 | `export FRONTEND_URL=http://localhost:3000` 후 재기동 |

## DB 초기화 (필요 시)

```bash
cd backend
docker compose down -v
docker compose up -d
# → 백엔드 재기동하면 Flyway V1~V4 자동 적용
```
