import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  scenarios: {
    transactions: {
      executor: 'ramping-arrival-rate',
      startRate: 50,
      timeUnit: '1s',
      preAllocatedVUs: 100,
      maxVUs: 500,
      stages: [
        { duration: '3m', target: 500 },   // разгон до 500 RPS
        { duration: '11m', target: 500 },  // удержание
        { duration: '3m', target: 0 },     // спад
      ],
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<1000'],  // 95% быстрее 1 секунды
    http_req_failed: ['rate<0.05'],     // ошибок меньше 5% (нагрузочный тест)
  },
};

export default function () {
  const url = 'http://gateway:8080/api/v1/transactions';
  const payload = JSON.stringify({
    externalId: uuidv4(),
    cardToken: 'card-' + Math.floor(Math.random() * 100000),
    amount: 100.00,
    currency: 'EUR',
    merchantName: 'Shop',
    merchantCountry: 'GERMANY',
    occurredAt: new Date().toISOString(),
  });
  const params = { headers: { 'Content-Type': 'application/json' } };

  const res = http.post(url, payload, params);
  check(res, { 'status 202': (r) => r.status === 202 });
}

function uuidv4() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}