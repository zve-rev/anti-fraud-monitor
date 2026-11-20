package com.zverev.alerting.controller;

import com.zverev.alerting.dto.TransactionResponse;
import com.zverev.alerting.service.TransactionQueryService;
import com.zverev.entity.enums.TransactionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/v1/transactions")
@RequiredArgsConstructor
public class TransactionQueryController {

    private final TransactionQueryService transactionQueryService;

    @GetMapping
    public Page<TransactionResponse> listAllTransactions(
            @RequestParam(required = false) TransactionStatus status,
            @PageableDefault(size = 20, sort = {"createdAt", "id"}, direction = Sort.Direction.DESC) Pageable pageable) {
        return transactionQueryService.findAllTransactions(status, pageable);
    }

    @GetMapping("/{id}")
    public TransactionResponse getTransactionById(@PathVariable Long id) {
        return transactionQueryService.findTransactionById(id);
    }
}