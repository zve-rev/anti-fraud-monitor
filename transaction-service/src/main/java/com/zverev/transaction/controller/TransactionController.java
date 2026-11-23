package com.zverev.transaction.controller;

import com.zverev.entity.enums.TransactionStatus;
import com.zverev.transaction.dto.AlertResponse;
import com.zverev.transaction.dto.TransactionCreatedResponse;
import com.zverev.transaction.dto.TransactionRequest;
import com.zverev.transaction.dto.TransactionResponse;
import com.zverev.transaction.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @GetMapping
    public Page<TransactionResponse> listAllTransactions(
            @RequestParam(required = false) TransactionStatus status,
            @PageableDefault(size = 20, sort = {"createdAt", "id"}, direction = Sort.Direction.DESC) Pageable pageable) {
        return transactionService.findAllTransactions(status, pageable);
    }

    @GetMapping("/{id}")
    public TransactionResponse getTransactionById(@PathVariable Long id) {
        return transactionService.findTransactionById(id);
    }

    @GetMapping("/{id}/alerts")
    public Page<AlertResponse> getTransactionAlerts(
            @PathVariable Long id,
            @PageableDefault(size = 20, sort = {"createdAt", "id"}, direction = Sort.Direction.DESC) Pageable pageable) {
        return transactionService.findTransactionAlerts(id, pageable);
    }

    @PostMapping
    public ResponseEntity<TransactionCreatedResponse> createTransaction(@Valid @RequestBody TransactionRequest request) {
        TransactionCreatedResponse response = transactionService.process(request);
        URI location = URI.create("/api/v1/transactions/" + response.transactionId());
        return ResponseEntity.accepted().location(location).body(response);
    }
}