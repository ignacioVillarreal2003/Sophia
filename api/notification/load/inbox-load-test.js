import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8082';
const TOKEN = __ENV.TOKEN;

const inboxLatency = new Trend('inbox_latency', true);
const rateLimited = new Rate('rate_limited');

export const options = {
  scenarios: {
    browse: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 20 },
        { duration: '2m', target: 20 },
        { duration: '30s', target: 100 },
        { duration: '2m', target: 100 },
        { duration: '30s', target: 0 },
      ],
      gracefulRampDown: '30s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{expected_response:true}': ['p(95)<400', 'p(99)<1000'],
    inbox_latency: ['p(95)<400'],
    rate_limited: ['rate<0.05'],
  },
};

const params = {
  headers: {
    Authorization: `Bearer ${TOKEN}`,
    Accept: 'application/json',
  },
  tags: { name: 'inbox' },
};

export function setup() {
  if (!TOKEN) {
    throw new Error('Set TOKEN to a JWT with NOTIFICATION_READ and NOTIFICATION_UPDATE');
  }
}

export default function () {
  group('inbox', () => {
    const list = http.get(`${BASE_URL}/api/v1/notifications?size=20`, params);
    inboxLatency.add(list.timings.duration);
    rateLimited.add(list.status === 429);
    check(list, { 'inbox 200': (r) => r.status === 200 });

    const unread = http.get(`${BASE_URL}/api/v1/notifications/unread-count`, params);
    check(unread, { 'unread-count 200': (r) => r.status === 200 });

    const filtered = http.get(
      `${BASE_URL}/api/v1/notifications?unreadOnly=true&severity=INFO&sort=createdAt,desc&size=20`,
      params,
    );
    check(filtered, { 'filtered 200': (r) => r.status === 200 });

    const groups = http.get(`${BASE_URL}/api/v1/notifications/groups`, params);
    check(groups, { 'groups 200': (r) => r.status === 200 });
  });

  sleep(1);
}
