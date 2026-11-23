package com.zverev.transaction.repository;

import com.zverev.entity.OutboxEvent;
import com.zverev.entity.Transaction;
import com.zverev.entity.enums.CurrencyCode;
import com.zverev.entity.enums.MerchantCountry;
import com.zverev.entity.enums.OutboxEventStatus;
import com.zverev.repository.OutboxEventRepository;
import com.zverev.repository.TransactionRepository;
import com.zverev.transaction.config.JpaConfiguration;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfiguration.class)
class TransactionRepositoryIntegrationTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("antifraud_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    @DisplayName("Should persist transaction and outbox event in PostgreSQL")
    void shouldPersistTransactionAndOutboxEvent() {
        UUID externalId = UUID.randomUUID();

        Transaction transaction = Transaction.builder()
                .externalId(externalId)
                .cardToken("card-token-1234")
                .amount(new BigDecimal("125.5000"))
                .currencyCode(CurrencyCode.EUR)
                .merchantName("Test Merchant")
                .merchantCountry(MerchantCountry.GERMANY)
                .occurredAt(Instant.now())
                .createdAt(Instant.now())
                .build();

        Transaction savedTransaction = transactionRepository.saveAndFlush(transaction);

        OutboxEvent outboxEvent = new OutboxEvent(
                savedTransaction.getExternalId().toString(),
                "{\"externalId\":\"" + externalId + "\"}",
                "transactions.raw",
                savedTransaction.getCardToken()
        );

        OutboxEvent savedOutboxEvent = outboxEventRepository.saveAndFlush(outboxEvent);

        assertThat(savedTransaction.getId()).isNotNull();
        assertThat(savedTransaction.getStatus().name()).isEqualTo("NEW");

        assertThat(transactionRepository.findByExternalId(externalId))
                .isPresent();

        assertThat(savedOutboxEvent.getId()).isNotNull();
        assertThat(savedOutboxEvent.getStatus())
                .isEqualTo(OutboxEventStatus.PENDING);
    }

    @Test
    @DisplayName("Should reject transactions with duplicate externalId")
    void shouldRejectTransactionWithDuplicateExternalId() {
        UUID externalId = UUID.randomUUID();

        Transaction firstTransaction = Transaction.builder()
                .externalId(externalId)
                .cardToken("card-token-1111")
                .amount(new BigDecimal("100.0000"))
                .currencyCode(CurrencyCode.EUR)
                .merchantName("First Merchant")
                .merchantCountry(MerchantCountry.GERMANY)
                .occurredAt(Instant.now())
                .createdAt(Instant.now())
                .build();

        Transaction duplicateTransaction = Transaction.builder()
                .externalId(externalId)
                .cardToken("card-token-2222")
                .amount(new BigDecimal("200.0000"))
                .currencyCode(CurrencyCode.EUR)
                .merchantName("Second Merchant")
                .merchantCountry(MerchantCountry.GERMANY)
                .occurredAt(Instant.now())
                .createdAt(Instant.now())
                .build();

        transactionRepository.saveAndFlush(firstTransaction);

        assertThatThrownBy(() ->
                transactionRepository.saveAndFlush(duplicateTransaction)
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Should preserve amount precision after database round-trip")
    void shouldPreserveAmountPrecision() {
        UUID externalId = UUID.randomUUID();
        BigDecimal expectedAmount = new BigDecimal("125.5000");

        Transaction transaction = Transaction.builder()
                .externalId(externalId)
                .cardToken("card-token-1234")
                .amount(expectedAmount)
                .currencyCode(CurrencyCode.EUR)
                .merchantName("Test Merchant")
                .merchantCountry(MerchantCountry.GERMANY)
                .occurredAt(Instant.now())
                .createdAt(Instant.now())
                .build();

        transactionRepository.saveAndFlush(transaction);

        entityManager.clear();

        Transaction reloadedTransaction = transactionRepository
                .findByExternalId(externalId)
                .orElseThrow();

        assertThat(reloadedTransaction.getAmount())
                .isEqualByComparingTo(expectedAmount);

        assertThat(reloadedTransaction.getAmount().scale())
                .isEqualTo(4);
    }

    @Test
    @DisplayName("Should reject transaction with null cardToken")
    void shouldRejectTransactionWithNullCardToken() {
        UUID externalId = UUID.randomUUID();

        assertThatThrownBy(() ->
                jdbcTemplate.update("""
                    INSERT INTO transactions (
                        external_id,
                        amount,
                        currency_code,
                        merchant_name,
                        merchant_country,
                        status,
                        occurred_at,
                        created_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())
                    """,
                        externalId,
                        new BigDecimal("100.0000"),
                        "EUR",
                        "Test Merchant",
                        "GERMANY",
                        "NEW"
                )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }
}
