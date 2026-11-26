import { login } from '../lib/auth.js';
import { createTransaction } from '../lib/transactions.js';

export const options = {
    scenarios: {
        smoke_test: {
            executor: 'shared-iterations',
            vus: 1,
            iterations: 1,
            maxDuration: '30s',
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.01'],
        http_req_duration: ['p(95)<2000'],
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