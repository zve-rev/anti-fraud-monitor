import { login } from '../lib/auth.js';
import { createTransaction } from '../lib/transactions.js';

export const options = {
    scenarios: {
        spike_test: {
            executor: 'ramping-arrival-rate',
            startRate: 5,
            timeUnit: '1s',
            stages: [
                { target: 5, duration: '1m' },
                { target: 100, duration: '10s' },
                { target: 100, duration: '1m' },
                { target: 5, duration: '10s' },
                { target: 5, duration: '1m' },
                { target: 0, duration: '30s' },
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
        dropped_iterations: ['count==0'],
    },
};

export function setup() {
    const token = login();
    return { token };
}

export default function (data) {
    createTransaction(data.token);
}