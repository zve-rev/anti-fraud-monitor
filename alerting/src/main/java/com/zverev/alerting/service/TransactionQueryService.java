package com.zverev.alerting.service;

import com.zverev.alerting.dto.TransactionResponse;
import com.zverev.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.zverev.alerting.dto.TransactionResponse.toTransactionResponse;

@Service
public class TransactionQueryService {

    private final TransactionRepository transactionRepository;

    public TransactionQueryService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> findAllTransactions() {
        return transactionRepository.findAll()
                .stream()
                .map(transaction -> {
                    String cardToken = transaction.getCardToken();
                    String cardTokenMasked = maskCard(cardToken);
                    return toTransactionResponse(transaction, cardTokenMasked);
                })
                .toList();
    }

    private String maskCard(String cardToken) {
        if (cardToken == null || cardToken.length() < 4) {
            return "****";
        }
        return "**** " + cardToken.substring(cardToken.length() - 4);
    }
}