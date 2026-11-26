import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL } from './auth.js';
import {
    acceptedTransactions,
    failedTransactions,
    transactionSuccessRate,
    transactionResponseTime,
} from './metrics.js';

export function createTransaction(token) {
    const payload = {
        externalId: crypto.randomUUID(),
        cardToken: `card-${__VU}-${__ITER}`,
        amount: 125.5000,
        currency: 'EUR',
        merchantName: 'Performance Test Merchant',
        merchantCountry: 'GERMANY',
        occurredAt: new Date(Date.now() - 10000).toISOString(),
    };

    const response = http.post(
        `${BASE_URL}/api/v1/transactions`,
        JSON.stringify(payload),
        {
            headers: {
                'Content-Type': 'application/json',
                Authorization: `Bearer ${token}`,
            },
            tags: {
                name: 'POST /api/v1/transactions',
            },
        }
    );

    const successful = check(response, {
        'transaction returns HTTP 202': (r) => r.status === 202,
        'transaction response contains transactionId': (r) => {
            if (r.status !== 202) {
                return false;
            }

            try {
                return Boolean(r.json('transactionId'));
            } catch (_) {
                return false;
            }
        },
    });

    if (!successful) {
        console.error(
            `Transaction failed: status=${response.status}, body=${response.body}`
        );
    }

    transactionResponseTime.add(response.timings.duration);
    transactionSuccessRate.add(successful);

    if (successful) {
        acceptedTransactions.add(1);
    } else {
        failedTransactions.add(1);
    }

    return successful;
}