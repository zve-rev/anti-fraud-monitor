package com.zverev.riskassessment.unit.guard;

import com.zverev.riskassessment.guard.VelocityGuard;
import com.zverev.shared.entity.Transaction;
import com.zverev.shared.entity.enums.AlertSeverity;
import com.zverev.shared.entity.enums.CurrencyCode;
import com.zverev.shared.entity.enums.MerchantCountry;
import com.zverev.shared.entity.enums.Rule;
import com.zverev.riskassessment.dto.GuardViolation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VelocityGuardTest {

    private static final String CARD_TOKEN = "1234567890123456";
    private static final String REDIS_KEY = "velocity:card:" + CARD_TOKEN;

    private StringRedisTemplate redisTemplate;
    private ZSetOperations<String, String> zSet;
    private VelocityGuard guard;
    private Transaction transaction;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        zSet = mock(ZSetOperations.class);

        when(redisTemplate.opsForZSet()).thenReturn(zSet);

        guard = new VelocityGuard(redisTemplate);

        transaction = Transaction.builder()
                .externalId(UUID.randomUUID())
                .cardToken(CARD_TOKEN)
                .amount(new BigDecimal("1000"))
                .currencyCode(CurrencyCode.EUR)
                .merchantName("Test Merchant")
                .merchantCountry(MerchantCountry.values()[0])
                .occurredAt(Instant.now())
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void shouldReturnEmptyWhenTransactionCountDoesNotExceedLimit() {
        when(zSet.size(REDIS_KEY)).thenReturn(5L);

        Optional<GuardViolation> result = guard.evaluate(transaction);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnCriticalViolationWhenTransactionCountExceedsLimit() {
        when(zSet.size(REDIS_KEY)).thenReturn(6L);

        Optional<GuardViolation> result = guard.evaluate(transaction);

        assertThat(result).isPresent();
        assertThat(result.get().severity()).isEqualTo(AlertSeverity.CRITICAL);
        assertThat(result.get().message())
                .contains("Velocity exceeded for card")
                .contains("6 transactions in last 5 minutes");
    }

    @Test
    void shouldRemoveExpiredTransactions() {
        when(zSet.size(REDIS_KEY)).thenReturn(1L);

        guard.evaluate(transaction);

        verify(zSet).removeRangeByScore(
                eq(REDIS_KEY),
                eq(0.0),
                anyDouble()
        );
    }

    @Test
    void shouldAddCurrentTransactionToRedisSortedSet() {
        when(zSet.size(REDIS_KEY)).thenReturn(1L);

        guard.evaluate(transaction);

        verify(zSet).add(
                eq(REDIS_KEY),
                eq(transaction.getExternalId().toString()),
                anyDouble()
        );
    }

    @Test
    void shouldSetExpirationForRedisKey() {
        when(zSet.size(REDIS_KEY)).thenReturn(1L);

        guard.evaluate(transaction);

        verify(redisTemplate).expire(
                REDIS_KEY,
                Duration.ofMinutes(5)
        );
    }

    @Test
    void shouldReturnEmptyWhenRedisReturnsNullCount() {
        when(zSet.size(REDIS_KEY)).thenReturn(null);

        Optional<GuardViolation> result = guard.evaluate(transaction);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnVelocityRule() {
        assertThat(guard.getRule()).isEqualTo(Rule.VELOCITY);
    }
}
