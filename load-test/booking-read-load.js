/**
 * Phase 5 — k6 읽기 부하 테스트: 인덱스 전/후 비교
 *
 * 인덱스 효과가 잘 드러나는 읽기 부하 중심.
 * HOLD 부하는 락/트랜잭션 영향이 지배적이므로 보조 시나리오.
 *
 * 사전 준비:
 *   1. 서버 기동: cd backend && source .env && ./gradlew bootRun
 *   2. (내 예매 목록 테스트 시) heavy user로 로그인하여 JWT 획득
 *
 * 실행:
 *   k6 run -e SCENARIO=seatmap load-test/booking-read-load.js
 *   k6 run -e SCENARIO=my_reservations -e HEAVY_EMAIL=bench1@test.com -e HEAVY_PASSWORD=hashed load-test/booking-read-load.js
 *
 * 환경 변수:
 *   SCENARIO: seatmap | my_reservations (기본: seatmap)
 *   BASE_URL: 서버 URL (기본: http://localhost:8080)
 *   EVENT_ID: 좌석맵 조회 대상 이벤트 ID (기본: 55, hot event)
 *   HEAVY_EMAIL: heavy user 이메일 (기본: bench1@test.com)
 *   HEAVY_PASSWORD: heavy user 비밀번호
 *   VUS: VU 수 (기본: 50)
 *   DURATION: 테스트 시간 (기본: 30s)
 */

import http from 'k6/http';
import { check, sleep } from 'k6';

const SCENARIO = __ENV.SCENARIO || 'seatmap';
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const EVENT_ID = __ENV.EVENT_ID || '55';
const VUS = parseInt(__ENV.VUS || '50');
const DURATION = __ENV.DURATION || '30s';

export const options = {
  scenarios: {
    default: {
      executor: 'constant-vus',
      vus: VUS,
      duration: DURATION,
    },
  },
  thresholds: {
    http_req_duration: ['p(99)<10000'],
  },
};

export function setup() {
  if (SCENARIO === 'my_reservations') {
    // heavy user로 로그인하여 JWT 획득
    const email = __ENV.HEAVY_EMAIL || 'bench1@test.com';
    const password = __ENV.HEAVY_PASSWORD || 'hashed';
    const loginRes = http.post(
      `${BASE_URL}/api/auth/login`,
      JSON.stringify({ email, password }),
      { headers: { 'Content-Type': 'application/json' } }
    );
    if (loginRes.status === 200) {
      const body = JSON.parse(loginRes.body);
      console.log(`Login OK, token: ${body.accessToken.substring(0, 20)}...`);
      return { token: body.accessToken };
    } else {
      console.log(`Login FAILED: ${loginRes.status} ${loginRes.body}`);
      return { token: null };
    }
  }
  return {};
}

export default function (data) {
  if (SCENARIO === 'seatmap') {
    // 좌석맵 조회 — 인증 불필요 (SecurityConfig: GET /api/events/** permitAll)
    const res = http.get(`${BASE_URL}/api/events/${EVENT_ID}/seats`);
    check(res, { 'seatmap 200': (r) => r.status === 200 });
  } else if (SCENARIO === 'my_reservations') {
    // 내 예매 목록 조회 — JWT 필요
    if (!data.token) {
      console.log('No token, skipping');
      return;
    }
    const res = http.get(`${BASE_URL}/api/me/reservations?page=0&size=20`, {
      headers: { Authorization: `Bearer ${data.token}` },
    });
    check(res, { 'my_reservations 200': (r) => r.status === 200 });
  }

  sleep(0.1);
}
