import { login } from '../lib/auth.js';
import { createTransaction } from '../lib/transactions.js';

export const options = {
    scenarios: {
        load_test: {
            executor: 'ramping-arrival-rate',
            startRate: 5,
            timeUnit: '1s',
            stages: [
                { target: 5, duration: '1m' },
                { target: 10, duration: '2m' },
                { target: 20, duration: '2m' },
                { target: 30, duration: '2m' },
                { target: 0, duration: '1m' },
            ],
            preAllocatedVUs: 10,
            maxVUs: 100,
            gracefulStop: '30s',
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.01'],
        http_req_duration: ['p(95)<2000', 'p(99)<4000'],
        transaction_success_rate: ['rate>0.99'],
        transaction_response_time: ['p(95)<2000'],
    },
};

export function setup() {
    const token = login();

    return { token };
}

export default function (data) {
    createTransaction(data.token);
}