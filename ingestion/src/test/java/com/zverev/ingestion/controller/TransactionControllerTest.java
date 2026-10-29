package com.zverev.ingestion.controller;

import com.zverev.entity.enums.TransactionStatus;
import com.zverev.ingestion.dto.TransactionCreatedResponse;
import com.zverev.ingestion.exception.TransactionAlreadyExistsException;
import com.zverev.ingestion.service.TransactionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = TransactionController.class,
        excludeAutoConfiguration = {
                org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration.class,
                org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration.class
        }
)
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransactionService transactionService;

    private static final String VALID_BODY = """
            {
              "externalId": "11111111-1111-1111-1111-111111111111",
              "cardToken": "card-1",
              "amount": 100,
              "currency": "EUR",
              "merchantName": "Shop",
              "merchantCountry": "GERMANY",
              "occurredAt": "2026-10-29T10:00:00Z"
            }
            """;

    @Test
    @DisplayName("valid request - 202 with NEW status")
    void createValid_returns202() throws Exception {
        when(transactionService.process(any()))
                .thenReturn(new TransactionCreatedResponse(1L, TransactionStatus.NEW));

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.transactionId").value(1))
                .andExpect(jsonPath("$.status").value("NEW"));
    }

    @Test
    @DisplayName("missing externalId - 400")
    void createInvalid_returns400() throws Exception {
        String invalidBody = """
                {
                  "cardToken": "card-1",
                  "amount": 100,
                  "currency": "EUR",
                  "merchantName": "Shop",
                  "merchantCountry": "GERMANY",
                  "occurredAt": "2026-10-29T10:00:00Z"
                }
                """;

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("negative amount - 400")
    void createNegativeAmount_returns400() throws Exception {
        String invalidBody = """
                {
                  "externalId": "11111111-1111-1111-1111-111111111111",
                  "cardToken": "card-1",
                  "amount": -50,
                  "currency": "EUR",
                  "merchantName": "Shop",
                  "merchantCountry": "GERMANY",
                  "occurredAt": "2026-10-29T10:00:00Z"
                }
                """;

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("duplicate transaction - 409 with error body")
    void createDuplicate_returns409() throws Exception {
        UUID externalId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(transactionService.process(any()))
                .thenThrow(new TransactionAlreadyExistsException(externalId));

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("duplicate_transaction"))
                .andExpect(jsonPath("$.externalId").value(externalId.toString()));
    }
}
