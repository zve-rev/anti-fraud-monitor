package com.zverev.transaction.integration.kafka;

import com.zverev.entity.enums.CurrencyCode;
import com.zverev.entity.enums.MerchantCountry;
import com.zverev.entity.enums.OutboxEventStatus;
import com.zverev.repository.OutboxEventRepository;
import com.zverev.transaction.TransactionServiceApplication;
import com.zverev.transaction.outbox.OutboxEventPoller;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(
        classes = TransactionServiceApplication.class,
        properties = "outbox.poller.fixed-delay=3600000"
)
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TransactionKafkaFlowIntegrationTest {

    private static final String TOPIC = "transactions.raw";

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("antifraud_test")
                    .withUsername("test")
                    .withPassword("test");

    @Container
    static final KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("apache/kafka:3.7.0"));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OutboxEventPoller outboxEventPoller;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Test
    @DisplayName("Should process transaction through HTTP, Outbox and Kafka")
    void shouldProcessTransactionThroughHttpOutboxAndKafka() throws Exception {
        UUID externalId = UUID.randomUUID();
        String cardToken = "card-token-1234";
        BigDecimal amount = new BigDecimal("125.5000");
        Instant occurredAt = Instant.now().minusSeconds(60);

        String requestBody = """
                {
                    "externalId": "%s",
                    "cardToken": "%s",
                    "amount": %s,
                    "currency": "%s",
                    "merchantName": "Test Merchant",
                    "merchantCountry": "%s",
                    "occurredAt": "%s"
                }
                """.formatted(
                externalId,
                cardToken,
                amount.toPlainString(),
                CurrencyCode.EUR,
                MerchantCountry.GERMANY,
                occurredAt
        );

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.transactionId").isNumber());

        Integer transactionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE external_id = ?",
                Integer.class,
                externalId
        );

        assertThat(transactionCount).isEqualTo(1);

        Long outboxEventId = jdbcTemplate.queryForObject(
                """
                SELECT id FROM outbox_events
                WHERE aggregate_id = ?
                """,
                Long.class,
                externalId.toString()
        );

        assertThat(outboxEventId).isNotNull();

        assertThat(outboxEventRepository.findById(outboxEventId))
                .isPresent()
                .get()
                .extracting(event -> event.getStatus())
                .isEqualTo(OutboxEventStatus.PENDING);

        outboxEventPoller.publishPendingEvents();

        ConsumerRecord<String, String> record = consumeMessage();

        assertThat(record.topic()).isEqualTo(TOPIC);
        assertThat(record.key()).isEqualTo(cardToken);

        assertThat(record.value())
                .contains(externalId.toString())
                .contains(cardToken)
                .contains("125.5000")
                .contains("EUR")
                .contains("Test Merchant")
                .contains("GERMANY");

        String eventStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM outbox_events WHERE id = ?",
                String.class,
                outboxEventId
        );

        Integer attempts = jdbcTemplate.queryForObject(
                "SELECT attempts FROM outbox_events WHERE id = ?",
                Integer.class,
                outboxEventId
        );

        assertThat(eventStatus).isEqualTo(OutboxEventStatus.SENT.name());
        assertThat(attempts).isEqualTo(1);
    }

    private ConsumerRecord<String, String> consumeMessage() {
        Properties properties = new Properties();

        properties.put(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafka.getBootstrapServers()
        );
        properties.put(
                ConsumerConfig.GROUP_ID_CONFIG,
                UUID.randomUUID().toString()
        );
        properties.put(
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class.getName()
        );
        properties.put(
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class.getName()
        );
        properties.put(
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest"
        );
        properties.put(
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
                false
        );

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties)) {
            consumer.subscribe(List.of(TOPIC));

            long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();

            while (System.nanoTime() < deadline) {
                ConsumerRecords<String, String> records =
                        consumer.poll(Duration.ofMillis(500));

                if (!records.isEmpty()) {
                    return records.iterator().next();
                }
            }
        }

        throw new AssertionError("Kafka message was not received within 15 seconds");
    }
}
