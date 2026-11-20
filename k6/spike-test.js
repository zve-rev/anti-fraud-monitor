import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const transactionErrors = new Rate('transaction_errors');
const transactionDuration = new Trend('transaction_duration');

const BASE_URL = __ENV.BASE_URL || 'http://gateway:8080';

export const options = {
    stages: [
        { duration: '20s', target: 10 },
        { duration: '10s', target: 300 },
        { duration: '30s', target: 300 },
        { duration: '10s', target: 10 },
        { duration: '20s', target: 10 },
    ],

    thresholds: {
        'http_req_failed{name:create_transaction}': ['rate<0.05'],
        'http_req_duration{name:create_transaction}': ['p(95)<2000'],
        'transaction_errors': ['rate<0.05'],
        'transaction_duration': ['p(95)<2000'],
        'checks': ['rate>0.95'],
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
        }
    );

    check(response, {
        'login successful': (r) => r.status === 200,
    });

    if (response.status !== 200) {
        throw new Error(`Login failed: ${response.status}`);
    }

    const body = response.json();

    if (!body.token) {
        throw new Error('JWT token is missing');
    }

    return {
        token: body.token,
    };
}

export default function (data) {
    const payload = {
        externalId: crypto.randomUUID(),
        cardToken: `spike-card-${__VU}-${__ITER}`,
        amount: 150.50,
        currency: 'EUR',
        merchantName: 'Spike Test Merchant',
        merchantCountry: 'GERMANY',
        occurredAt: new Date().toISOString(),
    };

    const startedAt = Date.now();

    const response = http.post(
        `${BASE_URL}/api/v1/transactions`,
        JSON.stringify(payload),
        {
            headers: {
                'Content-Type': 'application/json',
                Authorization: `Bearer ${data.token}`,
            },
            tags: {
                name: 'create_transaction',
            },
        }
    );

    const duration = Date.now() - startedAt;

    transactionDuration.add(duration);

    const success = check(response, {
        'transaction accepted (202)': (r) => r.status === 202,
        'transaction ID returned': (r) => {
            try {
                return Boolean(r.json('transactionId'));
            } catch (_) {
                return false;
            }
        },
    });

    transactionErrors.add(!success);

    sleep(0.2);
}
