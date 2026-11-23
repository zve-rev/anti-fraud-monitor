package com.zverev.transaction.integration.controller;

import com.zverev.entity.enums.AlertSeverity;
import com.zverev.entity.enums.CurrencyCode;
import com.zverev.entity.enums.MerchantCountry;
import com.zverev.entity.enums.Rule;
import com.zverev.entity.enums.TransactionStatus;
import com.zverev.transaction.dto.AlertResponse;
import com.zverev.transaction.dto.TransactionCreatedResponse;
import com.zverev.transaction.dto.TransactionRequest;
import com.zverev.transaction.dto.TransactionResponse;
import com.zverev.transaction.exception.GlobalExceptionHandler;
import com.zverev.transaction.exception.TransactionAlreadyExistsException;
import com.zverev.transaction.exception.TransactionNotFoundException;
import com.zverev.transaction.service.TransactionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionController.class)
@Import(GlobalExceptionHandler.class)
class TransactionControllerTest {

    private static final String BASE_URL = "/api/v1/transactions";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TransactionService transactionService;

    @Test
    @DisplayName("Should return all transactions")
    void shouldReturnAllTransactions() throws Exception {
        TransactionResponse response = createTransactionResponse();

        Page<TransactionResponse> page = new PageImpl<>(List.of(response));

        when(transactionService.findAllTransactions(eq(null), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].cardTokenMasked").value("****1234"))
                .andExpect(jsonPath("$.content[0].amount").value(125.5));

        verify(transactionService).findAllTransactions(eq(null), any(Pageable.class));
    }

    @Test
    @DisplayName("Should filter transactions by status")
    void shouldFilterTransactionsByStatus() throws Exception {
        TransactionStatus transactionStatus = TransactionStatus.values()[0];

        when(transactionService.findAllTransactions(eq(transactionStatus), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(createTransactionResponse())));

        mockMvc.perform(get(BASE_URL)
                        .param("status", transactionStatus.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));

        verify(transactionService).findAllTransactions(eq(transactionStatus), any(Pageable.class));
    }

    @Test
    @DisplayName("Should use default pagination and sorting")
    void shouldUseDefaultPaginationAndSorting() throws Exception {
        when(transactionService.findAllTransactions(eq(null), any(Pageable.class)))
                .thenReturn(Page.empty());

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk());

        org.mockito.ArgumentCaptor<Pageable> captor =
                org.mockito.ArgumentCaptor.forClass(Pageable.class);

        verify(transactionService).findAllTransactions(eq(null), captor.capture());

        Pageable pageable = captor.getValue();

        assertEquals(0, pageable.getPageNumber());
        assertEquals(20, pageable.getPageSize());
        assertEquals(
                Sort.Direction.DESC,
                pageable.getSort().getOrderFor("createdAt").getDirection()
        );
        assertEquals(
                Sort.Direction.DESC,
                pageable.getSort().getOrderFor("id").getDirection()
        );
    }

    @Test
    @DisplayName("Should apply custom pagination and sorting")
    void shouldApplyCustomPaginationAndSorting() throws Exception {
        when(transactionService.findAllTransactions(eq(null), any(Pageable.class)))
                .thenReturn(Page.empty());

        mockMvc.perform(get(BASE_URL)
                        .param("page", "2")
                        .param("size", "5")
                        .param("sort", "amount,asc"))
                .andExpect(status().isOk());

        org.mockito.ArgumentCaptor<Pageable> captor =
                org.mockito.ArgumentCaptor.forClass(Pageable.class);

        verify(transactionService).findAllTransactions(eq(null), captor.capture());

        Pageable pageable = captor.getValue();

        assertEquals(2, pageable.getPageNumber());
        assertEquals(5, pageable.getPageSize());
        assertEquals(
                Sort.Direction.ASC,
                pageable.getSort().getOrderFor("amount").getDirection()
        );
    }

    @Test
    @DisplayName("Should return transaction by ID")
    void shouldReturnTransactionById() throws Exception {
        TransactionResponse response = createTransactionResponse();

        when(transactionService.findTransactionById(1L))
                .thenReturn(response);

        mockMvc.perform(get(BASE_URL + "/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.externalId").value(response.externalId().toString()))
                .andExpect(jsonPath("$.cardTokenMasked").value("****1234"))
                .andExpect(jsonPath("$.merchantName").value("Test Merchant"))
                .andExpect(jsonPath("$.alerts").isArray());

        verify(transactionService).findTransactionById(1L);
    }

    @Test
    @DisplayName("Should return 404 when transaction does not exist")
    void shouldReturnNotFoundWhenTransactionDoesNotExist() throws Exception {
        when(transactionService.findTransactionById(999L))
                .thenThrow(new TransactionNotFoundException("Transaction 999 not found"));

        mockMvc.perform(get(BASE_URL + "/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Transaction not found"))
                .andExpect(jsonPath("$.detail").value("Transaction 999 not found"));

        verify(transactionService).findTransactionById(999L);
    }

    @Test
    @DisplayName("Should return 500 when unexpected exception occurs")
    void shouldReturnInternalServerErrorWhenUnexpectedExceptionOccurs() throws Exception {
        when(transactionService.findTransactionById(1L))
                .thenThrow(new RuntimeException("Database connection failed"));

        mockMvc.perform(get(BASE_URL + "/1"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title").value("Internal server error"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));

        verify(transactionService).findTransactionById(1L);
    }

    @Test
    @DisplayName("Should return transaction alerts")
    void shouldReturnTransactionAlerts() throws Exception {
        AlertResponse alert = createAlertResponse();

        when(transactionService.findTransactionAlerts(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(alert)));

        mockMvc.perform(get(BASE_URL + "/1/alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(10))
                .andExpect(jsonPath("$.content[0].message").value("Suspicious transaction"));

        verify(transactionService).findTransactionAlerts(eq(1L), any(Pageable.class));
    }

    @Test
    @DisplayName("Should return empty alerts page")
    void shouldReturnEmptyAlertsPage() throws Exception {
        when(transactionService.findTransactionAlerts(eq(1L), any(Pageable.class)))
                .thenReturn(Page.empty());

        mockMvc.perform(get(BASE_URL + "/1/alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    @DisplayName("Should create transaction")
    void shouldCreateTransaction() throws Exception {
        TransactionRequest request = createTransactionRequest();

        TransactionCreatedResponse response = new TransactionCreatedResponse(
                100L,
                TransactionStatus.values()[0]
        );

        when(transactionService.process(any(TransactionRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.transactionId").value(100))
                .andExpect(jsonPath("$.status").value(response.status().name()));

        verify(transactionService).process(any(TransactionRequest.class));
    }

    @Test
    @DisplayName("Should reject request with null external ID")
    void shouldRejectNullExternalId() throws Exception {
        assertValidationError(
                body -> body.putNull("externalId"),
                "externalId"
        );
    }

    @Test
    @DisplayName("Should reject blank card token")
    void shouldRejectBlankCardToken() throws Exception {
        assertValidationError(
                body -> body.put("cardToken", "   "),
                "cardToken"
        );
    }

    @Test
    @DisplayName("Should reject card token longer than 255 characters")
    void shouldRejectCardTokenLongerThan255Characters() throws Exception {
        assertValidationError(
                body -> body.put("cardToken", "a".repeat(256)),
                "cardToken"
        );
    }

    @Test
    @DisplayName("Should reject zero transaction amount")
    void shouldRejectZeroAmount() throws Exception {
        assertValidationError(
                body -> body.put("amount", 0),
                "amount"
        );
    }

    @Test
    @DisplayName("Should reject negative transaction amount")
    void shouldRejectNegativeAmount() throws Exception {
        assertValidationError(
                body -> body.put("amount", -10),
                "amount"
        );
    }

    @Test
    @DisplayName("Should reject amount with more than four decimal places")
    void shouldRejectAmountWithMoreThanFourDecimalPlaces() throws Exception {
        assertValidationError(
                body -> body.put("amount", new BigDecimal("100.12345")),
                "amount"
        );
    }

    @Test
    @DisplayName("Should reject null currency")
    void shouldRejectNullCurrency() throws Exception {
        assertValidationError(
                body -> body.putNull("currency"),
                "currency"
        );
    }

    @Test
    @DisplayName("Should reject blank merchant name")
    void shouldRejectBlankMerchantName() throws Exception {
        assertValidationError(
                body -> body.put("merchantName", ""),
                "merchantName"
        );
    }

    @Test
    @DisplayName("Should reject merchant name longer than 255 characters")
    void shouldRejectMerchantNameLongerThan255Characters() throws Exception {
        assertValidationError(
                body -> body.put("merchantName", "a".repeat(256)),
                "merchantName"
        );
    }

    @Test
    @DisplayName("Should reject null merchant country")
    void shouldRejectNullMerchantCountry() throws Exception {
        assertValidationError(
                body -> body.putNull("merchantCountry"),
                "merchantCountry"
        );
    }

    @Test
    @DisplayName("Should reject null occurredAt")
    void shouldRejectNullOccurredAt() throws Exception {
        assertValidationError(
                body -> body.putNull("occurredAt"),
                "occurredAt"
        );
    }

    @Test
    @DisplayName("Should reject future occurredAt")
    void shouldRejectFutureOccurredAt() throws Exception {
        assertValidationError(
                body -> body.put("occurredAt", "2099-01-01T00:00:00Z"),
                "occurredAt"
        );
    }

    @Test
    @DisplayName("Should reject malformed JSON")
    void shouldRejectMalformedJson() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.detail").value("Malformed request body"));

        verify(transactionService, never()).process(any(TransactionRequest.class));
    }

    @Test
    @DisplayName("Should reject unknown currency enum value")
    void shouldRejectUnknownCurrency() throws Exception {
        ObjectNode body = objectMapper.valueToTree(createTransactionRequest());
        body.put("currency", "UNKNOWN_CURRENCY");

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.detail").value("Malformed request body"));

        verify(transactionService, never()).process(any(TransactionRequest.class));
    }

    @Test
    @DisplayName("Should return conflict when transaction already exists")
    void shouldReturnConflictWhenTransactionAlreadyExists() throws Exception {
        TransactionRequest request = createTransactionRequest();

        when(transactionService.process(any(TransactionRequest.class)))
                .thenThrow(new TransactionAlreadyExistsException("Transaction already exists"));

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Duplicate transaction"))
                .andExpect(jsonPath("$.detail").value("Transaction already exists"));
    }

    private void assertValidationError(
            Consumer<ObjectNode> mutation,
            String field
    ) throws Exception {
        ObjectNode body = objectMapper.valueToTree(createTransactionRequest());

        mutation.accept(body);

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors." + field).exists());

        verify(transactionService, never()).process(any(TransactionRequest.class));
    }

    private TransactionRequest createTransactionRequest() {
        return new TransactionRequest(
                UUID.randomUUID(),
                "card-token-1234",
                new BigDecimal("125.5000"),
                CurrencyCode.values()[0],
                "Test Merchant",
                MerchantCountry.values()[0],
                Instant.parse("2026-01-01T10:00:00Z")
        );
    }

    private TransactionResponse createTransactionResponse() {
        return new TransactionResponse(
                1L,
                UUID.randomUUID(),
                "****1234",
                new BigDecimal("125.5000"),
                CurrencyCode.values()[0],
                "Test Merchant",
                MerchantCountry.values()[0],
                TransactionStatus.values()[0],
                Instant.parse("2026-01-01T10:00:00Z"),
                Instant.parse("2026-01-01T10:01:00Z"),
                List.of()
        );
    }

    private AlertResponse createAlertResponse() {
        return new AlertResponse(
                10L,
                Rule.values()[0],
                AlertSeverity.values()[0],
                "Suspicious transaction",
                Instant.parse("2026-01-01T10:02:00Z")
        );
    }
}
