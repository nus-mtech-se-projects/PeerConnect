import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: 10,
  duration: '1m',
  thresholds: {
    http_req_failed: ['rate<0.01'],
  },
};

const baseUrl = (__ENV.BASE_URL || '').trim().replace(/\/$/, '');

if (!baseUrl) {
  throw new Error('BASE_URL is required for the k6 smoke test.');
}

if (!/^https?:\/\//.test(baseUrl)) {
  throw new Error(`BASE_URL must start with http:// or https://. Received: ${baseUrl}`);
}

export default function () {
  const root = http.get(`${baseUrl}/`);
  check(root, {
    'root returns 200': (response) => response.status === 200,
  });

  const openApi = http.get(`${baseUrl}/v3/api-docs`);
  check(openApi, {
    'openapi returns 200': (response) => response.status === 200,
  });

  sleep(1);
}
