package com.zverev.ingestion.controller;

import com.zverev.ingestion.dto.TransactionCreatedResponse;
import com.zverev.ingestion.dto.TransactionRequest;
import com.zverev.ingestion.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    public ResponseEntity<TransactionCreatedResponse> createTransaction(@Valid @RequestBody TransactionRequest request) {
        TransactionCreatedResponse response = transactionService.process(request);
        URI location = URI.create("/api/v1/transactions/" + response.transactionId());
        return ResponseEntity.accepted()
                .location(location)
                .body(response);
    }
}