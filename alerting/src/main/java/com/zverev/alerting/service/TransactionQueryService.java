package com.zverev.alerting.service;

import com.zverev.alerting.dto.TransactionResponse;
import com.zverev.alerting.exception.ResourceNotFoundException;
import com.zverev.entity.Transaction;
import com.zverev.entity.enums.TransactionStatus;
import com.zverev.repository.TransactionRepository;
import com.zverev.util.CardMasker;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.zverev.alerting.dto.TransactionResponse.toTransactionResponse;

@Service
@RequiredArgsConstructor
public class TransactionQueryService {

    private final TransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public Page<TransactionResponse> findTransactions(TransactionStatus status, Pageable pageable) {
        Page<Transaction> page = (status == null)
                ? transactionRepository.findAll(pageable)
                : transactionRepository.findByStatus(status, pageable);
        return page.map(t -> toTransactionResponse(t, CardMasker.mask(t.getCardToken())));
    }

    @Transactional(readOnly = true)
    public TransactionResponse findTransactionById(Long id) {
        Transaction transaction = transactionRepository.findByIdWithAlerts(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction " + id + " not found"));
        String cardTokenMasked = CardMasker.mask(transaction.getCardToken());
        return toTransactionResponse(transaction, cardTokenMasked);
    }
}