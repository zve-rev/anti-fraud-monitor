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
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VelocityGuardTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    private VelocityGuard guard;

    @BeforeEach
    void setUp() {
        guard = new VelocityGuard(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    @DisplayName("first transaction in window - sets TTL, no violation")
    void firstTransaction_setsTtl() {
        when(valueOps.increment("velocity:card:card-1")).thenReturn(1L);

        assertThat(guard.evaluate(tx("card-1"))).isEmpty();
        verify(redisTemplate).expire(eq("velocity:card:card-1"), any(Duration.class));
    }

    @Test
    @DisplayName("5th transaction - no violation, TTL not reset")
    void fifthTransaction_noViolation() {
        when(valueOps.increment("velocity:card:card-1")).thenReturn(5L);

        assertThat(guard.evaluate(tx("card-1"))).isEmpty();
        verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("6th transaction - violation CRITICAL")
    void sixthTransaction_violation() {
        when(valueOps.increment("velocity:card:card-1")).thenReturn(6L);

        var result = guard.evaluate(tx("card-1"));

        assertThat(result).isPresent();
        assertThat(result.get().severity()).isEqualTo(AlertSeverity.CRITICAL);
        assertThat(result.get().message()).contains("6 transactions");
    }

    @Test
    @DisplayName("null from Redis - fail-open, no violation")
    void nullFromRedis_failOpen() {
        when(valueOps.increment("velocity:card:card-1")).thenReturn(null);

        assertThat(guard.evaluate(tx("card-1"))).isEmpty();
    }

    private Transaction tx(String cardToken) {
        return Transaction.builder()
                .amount(new BigDecimal("100"))
                .currencyCode(CurrencyCode.EUR)
                .cardToken(cardToken)
                .merchantName("Shop")
                .merchantCountry(MerchantCountry.GERMANY)
                .occurredAt(Instant.now())
                .createdAt(Instant.now())
                .externalId(UUID.randomUUID())
                .build();
    }
}