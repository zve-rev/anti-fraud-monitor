import http from 'k6/http';
import { check, fail } from 'k6';

export const BASE_URL = __ENV.BASE_URL || 'http://gateway:8080';

export function login() {
    const response = http.post(
        `${BASE_URL}/api/v1/auth/login`,
        JSON.stringify({
            login: __ENV.K6_LOGIN || 'staff',
            password: __ENV.K6_PASSWORD || 'staff123',
        }),
        {
            headers: {
                'Content-Type': 'application/json',
            },
            tags: {
                name: 'POST /api/v1/auth/login',
            },
        }
    );

    const successful = check(response, {
        'login returns HTTP 200': (r) => r.status === 200,
    });

    if (!successful) {
        fail(`Authentication failed: HTTP ${response.status}, body: ${response.body}`);
    }

    const body = response.json();

    if (!body.token) {
        fail('Authentication response does not contain token');
    }

    return body.token;
}