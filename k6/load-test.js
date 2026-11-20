import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = 'http://gateway:8080';

export const options = {
    stages: [
        { duration: '10s', target: 20 },
        { duration: '20s', target: 20 },
        { duration: '10s', target: 50 },
        { duration: '20s', target: 50 },
        { duration: '10s', target: 0 },
    ],
    thresholds: {
        'http_req_failed{name:create_transaction}': ['rate<0.01'],
        'http_req_duration{name:create_transaction}': ['p(95)<1000'],
        checks: ['rate>0.99'],
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
        cardToken: `card-${__VU}-${__ITER}`,
        amount: 150.50,
        currency: 'EUR',
        merchantName: 'Test Merchant',
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

    check(response, {
        'transaction returns HTTP 202': (r) => r.status === 202,
        'response contains transactionId': (r) =>
            r.status === 202 && r.json('transactionId') !== undefined,
    });

    sleep(0.2);
}
