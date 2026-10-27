package com.zverev.controller;

import com.zverev.dto.TransactionCreatedResponse;
import com.zverev.dto.TransactionRequest;
import com.zverev.dto.TransactionResponse;
import com.zverev.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("api/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping
    public List<TransactionResponse> listAllTransactions() {
        return transactionService.findAllTransactions();
    }

    @PostMapping
    public ResponseEntity<TransactionCreatedResponse> createTransaction(@Valid @RequestBody TransactionRequest request) {
        TransactionCreatedResponse response = transactionService.process(request);
        URI location = URI.create("api/v1/transactions/" + response.transactionId());
        return ResponseEntity.created(location).body(response);
    }
}
