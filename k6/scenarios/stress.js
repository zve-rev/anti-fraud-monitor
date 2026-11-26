import { login } from '../lib/auth.js';
import { createTransaction } from '../lib/transactions.js';

export const options = {
    scenarios: {
        stress_test: {
            executor: 'ramping-arrival-rate',
            startRate: 10,
            timeUnit: '1s',
            stages: [
                { target: 10, duration: '1m' },
                { target: 20, duration: '1m' },
                { target: 40, duration: '1m' },
                { target: 60, duration: '1m' },
                { target: 80, duration: '1m' },
                { target: 100, duration: '1m' },
                { target: 120, duration: '1m' },
                { target: 0, duration: '1m' },
            ],
            preAllocatedVUs: 20,
            maxVUs: 200,
            gracefulStop: '30s',
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.05'],
        http_req_duration: ['p(95)<3000', 'p(99)<5000'],
        transaction_success_rate: ['rate>0.95'],
        transaction_response_time: ['p(95)<3000'],
    },
};

export function setup() {
    const token = login();

    return { token };
}

export default function (data) {
    createTransaction(data.token);
}