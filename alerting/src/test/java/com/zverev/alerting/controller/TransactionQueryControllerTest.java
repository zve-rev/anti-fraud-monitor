package com.zverev.alerting.controller;

import com.zverev.alerting.dto.TransactionResponse;
import com.zverev.alerting.exception.ResourceNotFoundException;
import com.zverev.alerting.service.TransactionQueryService;
import com.zverev.entity.enums.CurrencyCode;
import com.zverev.entity.enums.MerchantCountry;
import com.zverev.entity.enums.TransactionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = TransactionQueryController.class,
        excludeAutoConfiguration = {
                org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration.class,
                org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration.class
        }
)
class TransactionQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransactionQueryService transactionQueryService;

    @Test
    @DisplayName("existing transaction - 200 with masked card")
    void getById_ok() throws Exception {
        when(transactionQueryService.findTransactionById(1L)).thenReturn(response());

        mockMvc.perform(get("/api/v1/transactions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.cardTokenMasked").value("**** 1234"))
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    @DisplayName("unknown transaction - 404 problem json")
    void getById_notFound() throws Exception {
        when(transactionQueryService.findTransactionById(99L))
                .thenThrow(new ResourceNotFoundException("Transaction 99 not found"));

        mockMvc.perform(get("/api/v1/transactions/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Not found"));
    }

    private TransactionResponse response() {
        return new TransactionResponse(
                1L,
                UUID.randomUUID(),
                "**** 1234",
                new BigDecimal("100"),
                CurrencyCode.EUR,
                "Shop",
                MerchantCountry.GERMANY,
                TransactionStatus.APPROVED,
                Instant.now(),
                Instant.now(),
                List.of()
        );
    }
}
