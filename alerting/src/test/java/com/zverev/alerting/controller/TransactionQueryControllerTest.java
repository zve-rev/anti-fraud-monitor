package com.zverev.alerting.controller;

import com.zverev.alerting.dto.TransactionResponse;
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
    @DisplayName("GET /api/v1/transactions - 200 with list")
    void findAll_returns200() throws Exception {
        TransactionResponse response = new TransactionResponse(
                1L,
                "**** 1234",
                new BigDecimal("100"),
                CurrencyCode.EUR,
                "Shop",
                MerchantCountry.GERMANY,
                TransactionStatus.APPROVED,
                Instant.parse("2026-10-29T10:00:00Z"),
                Instant.parse("2026-10-29T10:00:01Z"),
                List.of()
        );

        when(transactionQueryService.findAllTransactions()).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].cardTokenMasked").value("**** 1234"))
                .andExpect(jsonPath("$[0].status").value("APPROVED"))
                .andExpect(jsonPath("$[0].currency").value("EUR"))
                .andExpect(jsonPath("$[0].alerts").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/transactions - empty list returns 200")
    void findAll_emptyList_returns200() throws Exception {
        when(transactionQueryService.findAllTransactions()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }
}