package com.zverev.scoring.guard;

import com.zverev.entity.Transaction;
import com.zverev.entity.enums.AlertSeverity;
import com.zverev.entity.enums.CurrencyCode;
import com.zverev.entity.enums.MerchantCountry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VelocityGuardTest {

    private static final String KEY = "velocity:card:card-1";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOps;

    private VelocityGuard guard;

    @BeforeEach
    void setUp() {
        guard = new VelocityGuard(redisTemplate);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
    }

    @Test
    @DisplayName("5 transactions in window - no violation")
    void fiveInWindow_empty() {
        when(zSetOps.size(KEY)).thenReturn(5L);

        assertThat(guard.evaluate(tx())).isEmpty();
    }

    @Test
    @DisplayName("6 transactions in window - CRITICAL violation")
    void sixInWindow_violation() {
        when(zSetOps.size(KEY)).thenReturn(6L);

        var result = guard.evaluate(tx());

        assertThat(result).isPresent();
        assertThat(result.get().severity()).isEqualTo(AlertSeverity.CRITICAL);
        assertThat(result.get().message()).contains("6 transactions");
    }

    @Test
    @DisplayName("transaction is registered by externalId and ttl is refreshed")
    void registersTransactionAndRefreshesTtl() {
        when(zSetOps.size(KEY)).thenReturn(1L);
        Transaction tx = tx();

        guard.evaluate(tx);

        verify(zSetOps).removeRangeByScore(eq(KEY), anyDouble(), anyDouble());
        verify(zSetOps).add(eq(KEY), eq(tx.getExternalId().toString()), anyDouble());
        verify(redisTemplate).expire(eq(KEY), any(Duration.class));
    }

    @Test
    @DisplayName("null size from redis - no violation")
    void nullSize_empty() {
        when(zSetOps.size(KEY)).thenReturn(null);

        assertThat(guard.evaluate(tx())).isEmpty();
    }

    private Transaction tx() {
        return Transaction.builder()
                .externalId(UUID.randomUUID())
                .cardToken("card-1")
                .amount(new BigDecimal("100"))
                .currencyCode(CurrencyCode.EUR)
                .merchantName("Shop")
                .merchantCountry(MerchantCountry.GERMANY)
                .occurredAt(Instant.now())
                .createdAt(Instant.now())
                .build();
    }
}
