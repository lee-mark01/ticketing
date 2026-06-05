# CONCEPTS — 5개 개념 → 구현 위치 → 발표 증거물

> 이 문서는 두 가지 역할을 한다: (1) 각 개념을 코드의 어디서 보여줄지, (2) PPT에 넣을 증거물 체크리스트.
> 측정 산출물은 `docs/evidence/`에 모은다.

## ① 데이터베이스 설계
- 구현 위치: `V1__init.sql`, JPA 엔티티 8개 (`backend/src/main/java/com/ticketing/*/entity/`)
- 증거물:
  - [ ] ERD 다이어그램 1장
  - [ ] 엔티티 관계 설명 (DATA_MODEL.md 요약)

## ② 정규화
- 구현 위치: 스키마 구조 (seats / event_seats 분리, reservation_items 교차)
- 발표 포인트: 좌석맵 native SQL에서 두 테이블 JOIN이 정규화의 결과임을 보여줌
- 증거물:
  - [ ] 3NF 만족 근거 설명 (갱신 이상 방지)
  - [ ] 의도적 비정규화(`available_seat_count`)와 그 트레이드오프 설명
  - [ ] 같은 물리좌석이 공연별로 다른 가격을 가지는 시드 데이터 예시

## ③ 인덱싱
- 구현 위치: `V3__add_indexes.sql`, native 좌석맵 쿼리 (`EventSeatRepository.findSeatMapByEventId`)
- 증거물:
  - [x] 핫패스 쿼리 5개의 `EXPLAIN ANALYZE` **인덱스 추가 전/후 비교** → `docs/evidence/phase5-indexing.md`
  - [x] 복합 인덱스 사용 사례: `event_seats(event_id, status)` — AVAILABLE 조회 85% 개선
  - [ ] EXPLAIN 원문: `docs/evidence/phase5/before-*.txt`, `after-*.txt`

## ④ 트랜잭션
- 구현 위치: `ReservationService.confirmReservation()` (`@Transactional`)
- 증거물:
  - [x] 예매·좌석상태·결제기록이 원자적으로 처리되는 코드 → `ReservationService.java`
  - [x] **중간 실패 시 전체 롤백**을 증명하는 테스트 7개 → `docs/evidence/phase3-transaction.md`
  - [x] CONFIRM 실패 시 좌석 HELD 유지, payment 미생성, availableSeatCount 변화 없음 검증

## ⑤ 동시성 컨트롤 (★ 발표 클라이맥스)
- 구현 위치: `ConcurrencyExperimentService` (naive / 비관적 락 / 낙관적 락 3종)
- 증거물:
  - [x] 락 없을 때 **중복판매 발생** 재현 → `ConcurrencyTest.naive_oversell` (active reservation_item ≥ 2)
  - [x] 비관적 락(`FOR UPDATE`) vs 낙관적 락(`@Version`) 비교 → `docs/evidence/phase4-concurrency.md`
  - [x] 격리 수준(READ COMMITTED vs SERIALIZABLE) 동작 차이 → `docs/evidence/phase4-isolation-demo.sql`
  - [ ] k6 부하테스트 결과 (스크립트 작성 완료, 실행 예정) → `load-test/booking-concurrency.js`
  - [x] 락 제거 시 실패 / 적용 시 통과하는 멀티스레드 테스트 → `ConcurrencyTest` 4개

## 보너스 (있으면 가산점)
- [ ] N+1 쿼리 발견 → fetch join 으로 개선한 before/after
- [ ] 데드락 재현 및 처리 전략 설명
