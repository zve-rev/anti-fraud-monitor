package com.zverev.scoring;

import com.zverev.entity.Transaction;
import com.zverev.entity.enums.AlertSeverity;
import com.zverev.entity.enums.CurrencyCode;
import com.zverev.entity.enums.MerchantCountry;
import com.zverev.entity.enums.TransactionStatus;
import com.zverev.repository.TransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class ScoringIntegrationTest {

    private static final String TOPIC = "transactions.raw";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:3.8.0");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    @DisplayName("small amount - APPROVED without alerts")
    void smallAmount_approved() {
        UUID externalId = UUID.randomUUID();
        String card = "card-" + externalId;
        Transaction saved = save(externalId, card, "100");

        publish(externalId, card, "100");

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Transaction tx = transactionRepository.findByIdWithAlerts(saved.getId()).orElseThrow();
            assertThat(tx.getStatus()).isEqualTo(TransactionStatus.APPROVED);
            assertThat(tx.getAlerts()).isEmpty();
        });
    }

    @Test
    @DisplayName("amount above threshold - REVIEW with one MEDIUM alert")
    void largeAmount_review() {
        UUID externalId = UUID.randomUUID();
        String card = "card-" + externalId;
        Transaction saved = save(externalId, card, "6000");

        publish(externalId, card, "6000");

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Transaction tx = transactionRepository.findByIdWithAlerts(saved.getId()).orElseThrow();
            assertThat(tx.getStatus()).isEqualTo(TransactionStatus.REVIEW);
            assertThat(tx.getAlerts()).hasSize(1);
            assertThat(tx.getAlerts().get(0).getSeverity()).isEqualTo(AlertSeverity.MEDIUM);
        });
    }

    @Test
    @DisplayName("duplicate event - alerts are not duplicated")
    void duplicateEvent_idempotent() {
        UUID externalId = UUID.randomUUID();
        String card = "card-" + externalId;
        Transaction saved = save(externalId, card, "6000");

        publish(externalId, card, "6000");

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Transaction tx = transactionRepository.findByIdWithAlerts(saved.getId()).orElseThrow();
            assertThat(tx.getStatus()).isEqualTo(TransactionStatus.REVIEW);
        });

        publish(externalId, card, "6000");

        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Transaction tx = transactionRepository.findByIdWithAlerts(saved.getId()).orElseThrow();
            assertThat(tx.getStatus()).isEqualTo(TransactionStatus.REVIEW);
            assertThat(tx.getAlerts()).hasSize(1);
        });
    }

    @Test
    @DisplayName("six transactions of one card - exactly one DECLINED")
    void velocity_oneDeclined() {
        String card = "card-" + UUID.randomUUID();
        List<Long> ids = new ArrayList<>();

        for (int i = 0; i < 6; i++) {
            UUID externalId = UUID.randomUUID();
            ids.add(save(externalId, card, "100").getId());
            publish(externalId, card, "100");
        }

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            List<Transaction> all = transactionRepository.findAllById(ids);
            assertThat(all).noneMatch(t -> t.getStatus() == TransactionStatus.NEW);
            assertThat(all).filteredOn(t -> t.getStatus() == TransactionStatus.DECLINED).hasSize(1);
        });
    }

    private Transaction save(UUID externalId, String cardToken, String amount) {
        return transactionRepository.save(Transaction.builder()
                .externalId(externalId)
                .cardToken(cardToken)
                .amount(new BigDecimal(amount))
                .currencyCode(CurrencyCode.EUR)
                .merchantName("Shop")
                .merchantCountry(MerchantCountry.GERMANY)
                .occurredAt(Instant.now())
                .createdAt(Instant.now())
                .build());
    }

    private void publish(UUID externalId, String cardToken, String amount) {
        String payload = """
                {"externalId":"%s","cardToken":"%s","amount":%s,"currency":"EUR","merchantName":"Shop","merchantCountry":"GERMANY","occurredAt":"%s"}
                """.formatted(externalId, cardToken, amount, Instant.now());
        kafkaTemplate.send(TOPIC, cardToken, payload);
    }
}
