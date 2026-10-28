package com.zverev.alerting.controller;

import com.zverev.alerting.dto.TransactionResponse;
import com.zverev.alerting.service.TransactionQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionQueryController {

    private final TransactionQueryService transactionQueryService;

    public TransactionQueryController(TransactionQueryService transactionQueryService) {
        this.transactionQueryService = transactionQueryService;
    }

    @GetMapping
    public List<TransactionResponse> findAll() {
        return transactionQueryService.findAllTransactions();
    }
}
