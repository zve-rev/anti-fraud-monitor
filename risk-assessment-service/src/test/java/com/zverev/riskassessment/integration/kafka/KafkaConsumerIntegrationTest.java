package com.zverev.riskassessment.integration.kafka;

import com.zverev.shared.dto.TransactionEvent;
import com.zverev.shared.entity.Transaction;
import com.zverev.shared.entity.enums.CurrencyCode;
import com.zverev.shared.entity.enums.MerchantCountry;
import com.zverev.shared.entity.enums.TransactionStatus;
import com.zverev.shared.repository.TransactionRepository;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Testcontainers
class KafkaConsumerIntegrationTest {

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

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(
                    DockerImageName.parse("apache/kafka-native:3.8.0")
            );

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", REDIS::getFirstMappedPort);

        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static KafkaTemplate<String, TransactionEvent> kafkaTemplate;

    @BeforeAll
    static void createKafkaProducer() {
        Map<String, Object> properties = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                KAFKA.getBootstrapServers()
        );

        DefaultKafkaProducerFactory<String, TransactionEvent> producerFactory =
                new DefaultKafkaProducerFactory<>(
                        properties,
                        new StringSerializer(),
                        new JacksonJsonSerializer<>()
                );

        kafkaTemplate = new KafkaTemplate<>(producerFactory);
    }

    @AfterAll
    static void closeKafkaProducer() {
        if (kafkaTemplate != null) {
            kafkaTemplate.destroy();
        }
    }

    @BeforeEach
    void cleanRedis() {
        redisTemplate.getConnectionFactory()
                .getConnection()
                .serverCommands()
                .flushDb();
    }

    @Test
    void shouldProcessTransactionAndApproveIt() throws Exception {
        UUID externalId = UUID.randomUUID();

        Transaction transaction = createTransaction(externalId);

        transactionRepository.save(transaction);

        TransactionEvent event = createEvent(externalId);

        kafkaTemplate.send(
                "transactions.raw",
                externalId.toString(),
                event
        ).get(10, TimeUnit.SECONDS);

        await()
                .atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> {
                    Transaction processed = transactionRepository
                            .findByIdWithAlerts(transaction.getId())
                            .orElseThrow();

                    assertThat(processed.getStatus())
                            .isEqualTo(TransactionStatus.APPROVED);

                    assertThat(processed.getAlerts()).isEmpty();
                });
    }

    @Test
    void shouldDeclineTransactionWhenVelocityLimitIsExceeded() throws Exception {
        UUID externalId = UUID.randomUUID();

        Transaction transaction = createTransaction(externalId);

        transactionRepository.save(transaction);

        String redisKey = "velocity:card:" + transaction.getCardToken();

        for (int i = 0; i < 5; i++) {
            redisTemplate.opsForZSet().add(
                    redisKey,
                    UUID.randomUUID().toString(),
                    System.currentTimeMillis()
            );
        }

        TransactionEvent event = createEvent(externalId);

        kafkaTemplate.send(
                "transactions.raw",
                externalId.toString(),
                event
        ).get(10, TimeUnit.SECONDS);

        await()
                .atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> {
                    Transaction processed = transactionRepository
                            .findByIdWithAlerts(transaction.getId())
                            .orElseThrow();

                    assertThat(processed.getStatus())
                            .isEqualTo(TransactionStatus.DECLINED);

                    assertThat(processed.getAlerts())
                            .hasSize(1);
                });
    }

    private Transaction createTransaction(UUID externalId) {
        return Transaction.builder()
                .externalId(externalId)
                .cardToken("1234567890123456")
                .amount(new BigDecimal("1000"))
                .currencyCode(CurrencyCode.EUR)
                .merchantName("Test Merchant")
                .merchantCountry(MerchantCountry.values()[0])
                .occurredAt(Instant.now())
                .createdAt(Instant.now())
                .build();
    }

    private TransactionEvent createEvent(UUID externalId) {
        return new TransactionEvent(
                externalId,
                "1234567890123456",
                new BigDecimal("1000"),
                CurrencyCode.EUR,
                "Test Merchant",
                MerchantCountry.values()[0],
                Instant.now()
        );
    }
}
