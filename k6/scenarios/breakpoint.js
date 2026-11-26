import { login } from '../lib/auth.js';
import { createTransaction } from '../lib/transactions.js';

export const options = {
    scenarios: {
        breakpoint_test: {
            executor: 'ramping-arrival-rate',
            startRate: 10,
            timeUnit: '1s',
            stages: [
                { target: 10, duration: '1m' },
                { target: 25, duration: '1m' },
                { target: 50, duration: '1m' },
                { target: 75, duration: '1m' },
                { target: 100, duration: '1m' },
                { target: 150, duration: '1m' },
                { target: 200, duration: '1m' },
                { target: 250, duration: '1m' },
                { target: 300, duration: '1m' },
                { target: 0, duration: '1m' },
            ],
            preAllocatedVUs: 50,
            maxVUs: 500,
            gracefulStop: '60s',
        },
    },
    thresholds: {
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