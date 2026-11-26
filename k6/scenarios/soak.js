import { login } from '../lib/auth.js';
import { createTransaction } from '../lib/transactions.js';

export const options = {
    scenarios: {
        soak_test: {
            executor: 'constant-arrival-rate',
            rate: 20,
            timeUnit: '1s',
            duration: '30m',
            preAllocatedVUs: 20,
            maxVUs: 100,
            gracefulStop: '30s',
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.01'],
        http_req_duration: ['p(95)<2000', 'p(99)<4000'],
        transaction_success_rate: ['rate>0.99'],
        transaction_response_time: ['p(95)<2000'],
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