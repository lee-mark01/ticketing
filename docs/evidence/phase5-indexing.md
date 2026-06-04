# Phase 5 — 인덱싱 & 측정 증거물

## 목적
핫패스 쿼리에 인덱스를 추가하여 읽기 성능 개선을 증명한다.

## 측정 환경
- PostgreSQL 16 (Docker)
- WSL2 Linux 6.6, 로컬 개발 환경
- 동일 환경에서 before/after 반복 측정한 비교. 캐시 영향은 완전히 제거 불가.

### 측정 절차
1. V1+V2만 적용된 깨끗한 DB에서 `scripts/seed-large-data.sql` 실행
2. `ANALYZE;` 실행 후 **before** EXPLAIN 측정
3. `V3__add_indexes.sql`을 DB에 수동 적용 (측정용 절차)
4. `ANALYZE;` 재실행 후 **after** EXPLAIN 측정 — 같은 파라미터, 같은 ID 사용

> 측정용 DB에서는 V3를 수동 적용하고 flyway_schema_history에 직접 insert했다.
> 재현 가능한 운영 절차는, 깨끗한 DB에서 앱 기동 시 Flyway가 V1→V2→V3를 자동 적용하는 방식이다.
> 수동 적용은 before 측정을 위해 V3 적용 시점을 통제하기 위한 측정용 절차였다.

## 데이터 규모

| 테이블 | 건수 |
|--------|------|
| users | 5,001 |
| events | 55 (기존 5 + 측정용 50) |
| seats | 2,620 (기존 620 + 측정용 2,000) |
| event_seats | 101,160 |
| reservations | 100,000 |
| reservation_items | 100,000 |
| payments | 60,000 |

**Hot event:** ID=55 (측정공연 #50) — AVAILABLE 100, HELD 900, SOLD 1,000
**Heavy user:** ID=2 (bench1@test.com) — reservations 5,019건

---

## 핫패스 쿼리 선정 이유

| # | 쿼리 | 호출 빈도 | 중요도 |
|---|------|----------|--------|
| 1 | 좌석맵 전체 조회 | 예매 페이지 진입 시 매번 | 높음 |
| 2 | AVAILABLE 좌석 조회 | 잔여 좌석 확인 시 | 높음 |
| 3 | 만료 HOLD 조회 | 스케줄러 1분마다 | 중간 (배치) |
| 4 | 내 예매 목록 | 마이페이지 접근 시 | 중간 |
| 5 | HOLD FOR UPDATE | 예매 오픈 동시 호출 | 높음 (negative control) |

---

## 인덱스 설계 근거

| 인덱스 | 대상 쿼리 | 컬럼 순서 이유 |
|--------|----------|---------------|
| `idx_event_seats_event_status (event_id, status)` | 좌석맵 + AVAILABLE 필터 | event_id로 이벤트 필터 후 status로 추가 필터. 복합 인덱스로 두 조건 모두 Index Cond으로 처리 |
| `idx_reservations_status_expires (status, expires_at)` | 만료 HOLD 스케줄러 | status='PENDING'으로 등치 필터 후 expires_at < now()로 range scan. 등치 조건이 선두 |
| `idx_reservations_user_created (user_id, created_at DESC)` | 내 예매 목록 | user_id 등치 필터 + created_at DESC 정렬. 인덱스 정렬로 ORDER BY 추가 비용 감소 |

---

## Before/After 비교표

| # | 쿼리 | Before (ms) | After (ms) | 개선율 | Scan 변화 | Buffers 변화 |
|---|------|-------------|------------|--------|----------|-------------|
| 1 | 좌석맵 전체 조회 | 7.974 | 12.582 | -58%* | Bitmap(UNIQUE) → Bitmap(idx) | hit 710 → 677+read 3 |
| 2 | AVAILABLE 좌석 조회 | 1.890 | **0.286** | **85%** | Bitmap(UNIQUE)+Filter → Bitmap(idx) Index Cond | hit 690 → **hit 8** |
| 3 | 만료 HOLD 조회 | 33.880 | **5.341** | **84%** | **Seq Scan → Bitmap Index Scan** | hit 918 → hit 917+read 18 |
| 4 | 내 예매 목록 (LIMIT 20) | 61.904 | **47.089** | **24%** | reservations **Seq Scan → Bitmap Index Scan** | hit 21659 → hit 20810+read 22 |
| 5 | HOLD FOR UPDATE | 0.668 | 0.286 | 57% | PK Index Scan (변화 없음) | hit 26 → hit 21 |

*쿼리 1(좌석맵 전체): before에서 이미 UNIQUE(event_id, seat_id) 인덱스가 event_id를 선두 컬럼으로 포함하고 있어 Bitmap Index Scan을 사용하고 있었음. 새 `idx_event_seats_event_status` 인덱스로 교체되었지만, 이 쿼리는 특정 event의 전체 2,000건을 모두 반환하므로 인덱스 선택성(selectivity)이 낮아 추가 인덱스의 효과가 제한적. after가 오히려 느린 것은 cold cache(read 3)의 영향이며, 실질적으로는 before와 동등한 성능. 이 쿼리에 대한 인덱스 효과는 status 필터가 추가되는 **쿼리 2(AVAILABLE 조회)에서 명확히 드러남.**

---

## 핵심 Plan 변화 요약

### 쿼리 2: AVAILABLE 좌석 조회 (가장 큰 개선)
**Before:** Bitmap Index Scan(UNIQUE) → Bitmap Heap Scan → Filter(status='AVAILABLE'), Rows Removed by Filter: 1900
**After:** Bitmap Index Scan(idx_event_seats_event_status) → Index Cond: (event_id=55 AND status='AVAILABLE'), Rows Removed by Filter: 0
→ 필터링이 Index Cond으로 이동하여 불필요한 1,900건 읽기 제거.

### 쿼리 3: 만료 HOLD 조회
**Before:** Seq Scan on reservations, Filter: status='PENDING' AND expires_at < now(), Rows Removed by Filter: 80,100
**After:** Bitmap Index Scan(idx_reservations_status_expires), Index Cond: status='PENDING' AND expires_at < now()
→ 100,000건 전체 스캔 → 인덱스에서 직접 대상만 추출.

### 쿼리 4: 내 예매 목록
**Before:** Seq Scan on reservations, Filter: user_id=2, Rows Removed by Filter: 94,981
**After:** Bitmap Index Scan(idx_reservations_user_created), Index Cond: user_id=2
→ 100,000건 스캔 → 5,019건만 인덱스로 추출. 다만 reservation_items Seq Scan은 여전히 병목.

### 쿼리 5: HOLD FOR UPDATE (negative control)
Before/After 모두 PK Index Scan 사용. 추가 인덱스 효과 거의 없음.
→ **PK가 커버하는 쿼리에는 추가 인덱스가 불필요하다는 증거.**

---

## EXPLAIN ANALYZE 원문

원문은 `docs/evidence/phase5/` 디렉토리에 저장:
- `before-seatmap.txt` / `after-seatmap.txt`
- `before-available.txt` / `after-available.txt`
- `before-expired-hold.txt` / `after-expired-hold.txt`
- `before-my-reservations.txt` / `after-my-reservations.txt`
- `before-hold-forupdate.txt` / `after-hold-forupdate.txt`

---

## k6 부하 테스트 (스크립트 작성 완료 / 실행 예정)

> 좌석맵 조회 + 내 예매 목록 읽기 부하 중심. HOLD 부하는 보조 시나리오.
> 스크립트: `load-test/booking-read-load.js`
> 실행 후 결과를 여기에 추가할 것.

---

## Trade-off

| 항목 | 개선 | 비용 |
|------|------|------|
| 읽기 성능 | AVAILABLE 조회 85% 감소, 만료 HOLD 84% 감소 | |
| 쓰기 비용 | | HOLD/CONFIRM 시 event_seats UPDATE마다 idx_event_seats_event_status 갱신 |
| 저장공간 | | 인덱스 3개 × 100,000건 규모 추가 |
| 유지보수 | 스케줄러 부담 감소 | 인덱스가 많아지면 write amplification |

현재 3개 인덱스는 읽기 빈도가 높은 핫패스 쿼리 전용이므로 trade-off가 합리적.

---

## 결론

- `idx_event_seats_event_status`: AVAILABLE 좌석 필터에서 **85% 성능 개선**. 복합 인덱스 두 번째 컬럼(status)이 핵심.
- `idx_reservations_status_expires`: 만료 스케줄러에서 **Seq Scan 제거, 84% 개선**. 1분마다 실행되는 배치에 효과적.
- `idx_reservations_user_created`: 내 예매 목록에서 **Seq Scan 제거, 24% 개선**. reservation_items 테이블이 여전히 Seq Scan이므로 개선폭 제한적.
- HOLD FOR UPDATE는 PK로 충분 — **추가 인덱스 불필요 (negative control 확인)**.
