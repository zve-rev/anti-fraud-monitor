package com.zverev.alerting.service;

import com.zverev.alerting.dto.TransactionResponse;
import com.zverev.entity.Transaction;
import com.zverev.repository.TransactionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
        return transactionRepository.findAllWithAlerts()
                .stream()
                .map(transaction -> {
                    String cardToken = transaction.getCardToken();
                    String cardTokenMasked = maskCard(cardToken);
                    return toTransactionResponse(transaction, cardTokenMasked);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public TransactionResponse findTransactionById(Long id) {
        Transaction transaction = transactionRepository.findByIdWithAlerts(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "transaction not found"));
        String cardTokenMasked = maskCard(transaction.getCardToken());
        return toTransactionResponse(transaction, cardTokenMasked);
    }

    private String maskCard(String cardToken) {
        if (cardToken == null || cardToken.length() < 4) {
            return "****";
        }
        return "**** " + cardToken.substring(cardToken.length() - 4);
    }
}