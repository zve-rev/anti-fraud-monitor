import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const BASE_URL = 'http://gateway:8080';

const transactionErrors = new Rate('transaction_errors');
const transactionDuration = new Trend('transaction_duration', true);

export const options = {
    stages: [
        { duration: '30s', target: 50 },
        { duration: '30s', target: 100 },
        { duration: '30s', target: 150 },
        { duration: '30s', target: 200 },
        { duration: '30s', target: 300 },
        { duration: '15s', target: 0 },
    ],

    thresholds: {
        'http_req_failed{name:create_transaction}': ['rate<0.01'],
        'http_req_duration{name:create_transaction}': ['p(95)<1000'],
        'transaction_errors': ['rate<0.01'],
        'transaction_duration': ['p(95)<1000'],
        'checks': ['rate>0.99'],
    },
};

export function setup() {
    const response = http.post(
        `${BASE_URL}/api/v1/auth/login`,
        JSON.stringify({
            login: 'staff',
            password: 'staff123',
        }),
        {
            headers: {
                'Content-Type': 'application/json',
            },
            tags: {
                name: 'login',
            },
        },
    );

    check(response, {
        'login returns HTTP 200': (r) => r.status === 200,
    });

    if (response.status !== 200) {
        throw new Error(`Login failed: ${response.status} ${response.body}`);
    }

    return {
        token: response.json('token'),
    };
}

export default function (data) {
    const transaction = {
        externalId: crypto.randomUUID(),
        cardToken: `stress-card-${__VU}-${__ITER}`,
        amount: 150.50,
        currency: 'EUR',
        merchantName: 'Stress Test Merchant',
        merchantCountry: 'GERMANY',
        occurredAt: new Date().toISOString(),
    };

    const response = http.post(
        `${BASE_URL}/api/v1/transactions`,
        JSON.stringify(transaction),
        {
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${data.token}`,
            },
            tags: {
                name: 'create_transaction',
            },
        },
    );

    transactionErrors.add(response.status !== 202);
    transactionDuration.add(response.timings.duration);

    check(response, {
        'transaction returns HTTP 202': (r) => r.status === 202,
        'response contains transactionId': (r) =>
            r.status === 202 && r.json('transactionId') !== undefined,
    });

    sleep(0.2);
}