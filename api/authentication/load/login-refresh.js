import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const EMAIL = __ENV.EMAIL || 'admin@sophia.local';
const PASSWORD = __ENV.PASSWORD || 'Admin123!';

let vuRefreshToken = null;

export const options = {
  scenarios: {
    login: {
      executor: 'constant-arrival-rate',
      exec: 'loginScenario',
      rate: Number(__ENV.LOGIN_RATE || 5),
      timeUnit: '1s',
      duration: __ENV.DURATION || '2m',
      preAllocatedVUs: 20,
      maxVUs: 100,
    },
    refresh: {
      executor: 'constant-vus',
      exec: 'refreshScenario',
      vus: Number(__ENV.REFRESH_VUS || 10),
      duration: __ENV.DURATION || '2m',
      startTime: '5s',
    },
  },
  thresholds: {
    'http_req_duration{scenario:login}': ['p(95)<1000'],
    'http_req_duration{scenario:refresh}': ['p(95)<300'],
    http_req_failed: ['rate<0.01'],
  },
};

function login(tags) {
  const response = http.post(`${BASE_URL}/api/v1/authentication/login`,
    JSON.stringify({ email: EMAIL, password: PASSWORD }),
    { headers: { 'Content-Type': 'application/json' }, tags });
  check(response, { 'login 200': (r) => r.status === 200 });
  return response.status === 200 ? response.json('data.refreshToken') : null;
}

export function loginScenario() {
  login({ scenario: 'login' });
  sleep(0.1);
}

export function refreshScenario() {
  if (!vuRefreshToken) {
    vuRefreshToken = login({ scenario: 'login' });
    if (!vuRefreshToken) {
      sleep(1);
      return;
    }
  }
  const response = http.post(`${BASE_URL}/api/v1/authentication/refresh`,
    JSON.stringify({ refreshToken: vuRefreshToken }),
    { headers: { 'Content-Type': 'application/json' }, tags: { scenario: 'refresh' } });
  const passed = check(response, { 'refresh 200': (r) => r.status === 200 });
  vuRefreshToken = passed ? response.json('data.refreshToken') : null;
  sleep(0.2);
}
