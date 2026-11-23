package com.zverev.riskassessment.integration.guard;

import com.zverev.shared.entity.Transaction;
import com.zverev.shared.entity.enums.AlertSeverity;
import com.zverev.shared.entity.enums.CurrencyCode;
import com.zverev.shared.entity.enums.MerchantCountry;
import com.zverev.riskassessment.dto.GuardViolation;
import com.zverev.riskassessment.guard.VelocityGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class VelocityGuardIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("antifraud")
                    .withUsername("antifraud")
                    .withPassword("antifraud");

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine")
                    .withExposedPorts(6379);

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", REDIS::getFirstMappedPort);
    }

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private VelocityGuard guard;

    private Transaction transaction;

    private static final String CARD_TOKEN = "1234567890123456";
    private static final String REDIS_KEY = "velocity:card:" + CARD_TOKEN;

    @BeforeEach
    void setUp() {
        redisTemplate.getConnectionFactory()
                .getConnection()
                .serverCommands()
                .flushDb();

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
    void shouldAddTransactionToRedisSortedSet() {
        guard.evaluate(transaction);

        Double score = redisTemplate.opsForZSet()
                .score(REDIS_KEY, transaction.getExternalId().toString());

        assertThat(score).isNotNull();
    }

    @Test
    void shouldReturnEmptyWhenTransactionCountDoesNotExceedLimit() {
        for (int i = 0; i < 4; i++) {
            redisTemplate.opsForZSet().add(
                    REDIS_KEY,
                    UUID.randomUUID().toString(),
                    System.currentTimeMillis()
            );
        }

        Optional<GuardViolation> result = guard.evaluate(transaction);

        assertThat(result).isEmpty();
        assertThat(redisTemplate.opsForZSet().size(REDIS_KEY)).isEqualTo(5L);
    }

    @Test
    void shouldReturnCriticalViolationWhenTransactionCountExceedsLimit() {
        for (int i = 0; i < 5; i++) {
            redisTemplate.opsForZSet().add(
                    REDIS_KEY,
                    UUID.randomUUID().toString(),
                    System.currentTimeMillis()
            );
        }

        Optional<GuardViolation> result = guard.evaluate(transaction);

        assertThat(result).isPresent();
        assertThat(result.get().severity()).isEqualTo(AlertSeverity.CRITICAL);
    }

    @Test
    void shouldSetExpirationForRedisKey() {
        guard.evaluate(transaction);

        Long ttl = redisTemplate.getExpire(REDIS_KEY);

        assertThat(ttl).isNotNull();
        assertThat(ttl).isPositive();
        assertThat(ttl).isLessThanOrEqualTo(300L);
    }

    @Test
    void shouldRemoveExpiredTransactions() {
        redisTemplate.opsForZSet().add(
                REDIS_KEY,
                UUID.randomUUID().toString(),
                System.currentTimeMillis() - 600_000
        );

        guard.evaluate(transaction);

        assertThat(redisTemplate.opsForZSet().size(REDIS_KEY)).isEqualTo(1L);
    }
}
