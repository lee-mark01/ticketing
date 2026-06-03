/**
 * Phase 4 — k6 부하 테스트: 락 전략별 동시성 비교
 *
 * 이 테스트는 barrier/hook 없이 실제 HTTP 요청을 보내는 realistic load test이다.
 * 성능 합격 기준이 아니라 전략별 trade-off 비교 자료로 사용한다.
 *
 * 사전 준비:
 *   1. experiment profile로 서버 기동:
 *      cd backend && source .env && SPRING_PROFILES_ACTIVE=experiment ./gradlew bootRun
 *   2. 실험용 사용자 시드:
 *      curl -X POST "http://localhost:8080/api/experiment/seed-users?count=200"
 *
 * 실행 예시:
 *   k6 run -e STRATEGY=PESSIMISTIC -e SCENARIO=smoke load-test/booking-concurrency.js
 *   k6 run -e STRATEGY=NAIVE -e SCENARIO=hot_seat load-test/booking-concurrency.js
 *
 * 환경 변수:
 *   STRATEGY: NAIVE | PESSIMISTIC | OPTIMISTIC (기본: PESSIMISTIC)
 *   SCENARIO: smoke | hot_seat | seat_pool | spike (기본: smoke)
 *   BASE_URL: 서버 URL (기본: http://localhost:8080)
 *   EVENT_ID: 테스트 대상 이벤트 ID (기본: 1)
 *
 * 로컬 환경에서 너무 무거우면 VUs를 낮춰도 된다.
 * 배포 환경에서는 500 VUs 이상까지 확장 가능.
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

// 커스텀 메트릭
const holdSuccess = new Counter('hold_success');
const holdFail = new Counter('hold_fail');
const holdConflict = new Counter('hold_conflict_409');
const holdServerError = new Counter('hold_server_error_500');

const STRATEGY = __ENV.STRATEGY || 'PESSIMISTIC';
const SCENARIO = __ENV.SCENARIO || 'smoke';
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const EVENT_ID = __ENV.EVENT_ID || '1';

// 시나리오 설정
const scenarios = {
  smoke: {
    executor: 'constant-vus',
    vus: 20,
    duration: '10s',
  },
  hot_seat: {
    executor: 'shared-iterations',
    vus: 100,
    iterations: 100,
  },
  seat_pool: {
    executor: 'constant-vus',
    vus: 200,
    duration: '60s',
  },
  spike: {
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      { duration: '10s', target: 200 },
      { duration: '30s', target: 200 },
      { duration: '10s', target: 0 },
    ],
  },
};

export const options = {
  scenarios: {
    default: scenarios[SCENARIO] || scenarios.smoke,
  },
  thresholds: {
    // 성능 합격 기준이 아니라 수집용. 절대 실패하지 않는 느슨한 값.
    http_req_duration: ['p(99)<30000'],
  },
};

// 시나리오 시작 전 reset
export function setup() {
  console.log(`Strategy: ${STRATEGY}, Scenario: ${SCENARIO}`);

  const resetRes = http.post(`${BASE_URL}/api/experiment/reset?eventId=${EVENT_ID}`);
  check(resetRes, { 'reset OK': (r) => r.status === 200 });

  // 좌석 ID 목록 가져오기 (seat pool용)
  const seatsRes = http.get(`${BASE_URL}/api/events/${EVENT_ID}/seats`);
  if (seatsRes.status === 200) {
    const body = JSON.parse(seatsRes.body);
    const seatIds = body.seats
      .filter((s) => s.status === 'AVAILABLE')
      .map((s) => s.eventSeatId);
    return { seatIds };
  }
  return { seatIds: [] };
}

export default function (data) {
  const userId = (__VU % 200) + 1; // 실험 사용자 1~200

  let seatId;
  if (SCENARIO === 'hot_seat') {
    // 모든 VU가 같은 좌석 1개에 집중
    seatId = data.seatIds[0];
  } else {
    // 랜덤 좌석 선택
    seatId = data.seatIds[Math.floor(Math.random() * data.seatIds.length)];
  }

  const url =
    `${BASE_URL}/api/experiment/events/${EVENT_ID}/holds` +
    `?strategy=${STRATEGY}&userId=${userId}&seatId=${seatId}`;

  const res = http.post(url);

  const success = check(res, {
    'hold created (201)': (r) => r.status === 201,
  });

  if (success) {
    holdSuccess.add(1);
  } else {
    holdFail.add(1);
    if (res.status === 409) holdConflict.add(1);
    if (res.status >= 500) holdServerError.add(1);
  }

  sleep(0.1); // 100ms 간격
}

// 테스트 종료 후 oversell 검증 안내
export function teardown(data) {
  console.log('=== 테스트 완료 ===');
  console.log('oversell 검증 SQL:');
  console.log(`
    SELECT ri.event_seat_id, COUNT(*) AS active_count
    FROM reservation_items ri
    JOIN reservations r ON r.id = ri.reservation_id
    WHERE r.status IN ('PENDING', 'CONFIRMED')
    GROUP BY ri.event_seat_id
    HAVING COUNT(*) > 1;
  `);
  console.log('위 쿼리 결과가 있으면 oversell 발생.');
}
