import { Counter, Rate, Trend } from 'k6/metrics';

export const acceptedTransactions = new Counter('accepted_transactions');

export const failedTransactions = new Counter('failed_transactions');

export const transactionSuccessRate = new Rate('transaction_success_rate');

export const transactionResponseTime = new Trend('transaction_response_time', true);