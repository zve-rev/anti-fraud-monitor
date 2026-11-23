package com.zverev.transaction.integration.service;

import com.zverev.entity.OutboxEvent;
import com.zverev.entity.enums.CurrencyCode;
import com.zverev.entity.enums.MerchantCountry;
import com.zverev.repository.OutboxEventRepository;
import com.zverev.transaction.TransactionServiceApplication;
import com.zverev.transaction.dto.TransactionRequest;
import com.zverev.transaction.outbox.OutboxEventPoller;
import com.zverev.transaction.service.TransactionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@Testcontainers
@SpringBootTest(classes = TransactionServiceApplication.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TransactionServiceIntegrationTest {

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
    private TransactionService transactionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private OutboxEventRepository outboxEventRepository;

    @MockitoBean
    private OutboxEventPoller outboxEventPoller;

    @Test
    @DisplayName("Should rollback transaction when outbox persistence fails")
    void shouldRollbackTransactionWhenOutboxPersistenceFails() {
        UUID externalId = UUID.randomUUID();

        TransactionRequest request = new TransactionRequest(
                externalId,
                "card-token-1234",
                new BigDecimal("125.5000"),
                CurrencyCode.EUR,
                "Test Merchant",
                MerchantCountry.GERMANY,
                Instant.now()
        );

        doThrow(new RuntimeException("Simulated outbox persistence failure"))
                .when(outboxEventRepository)
                .save(any(OutboxEvent.class));

        assertThatThrownBy(() -> transactionService.process(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Simulated outbox persistence failure");

        Integer transactionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE external_id = ?",
                Integer.class,
                externalId
        );

        Integer outboxEventCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE aggregate_id = ?",
                Integer.class,
                externalId.toString()
        );

        assertThat(transactionCount).isZero();
        assertThat(outboxEventCount).isZero();
    }

    @Test
    @DisplayName("Should persist transaction and outbox event atomically")
    void shouldPersistTransactionAndOutboxEventAtomically() {
        UUID externalId = UUID.randomUUID();

        TransactionRequest request = new TransactionRequest(
                externalId,
                "card-token-1234",
                new BigDecimal("125.5000"),
                CurrencyCode.EUR,
                "Test Merchant",
                MerchantCountry.GERMANY,
                Instant.now()
        );

        transactionService.process(request);

        Integer transactionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE external_id = ?",
                Integer.class,
                externalId
        );

        Integer outboxEventCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE aggregate_id = ?",
                Integer.class,
                externalId.toString()
        );

        assertThat(transactionCount).isEqualTo(1);
        assertThat(outboxEventCount).isEqualTo(1);
    }
}
