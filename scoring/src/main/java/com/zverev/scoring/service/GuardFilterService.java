package com.zverev.scoring.service;

import com.zverev.entity.Alert;
import com.zverev.entity.Transaction;
import com.zverev.entity.enums.TransactionStatus;
import com.zverev.scoring.guard.Guard;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class GuardFilterService {

    private final List<Guard> guards;

    public GuardFilterService(List<Guard> guards) {
        this.guards = guards;
    }

    public void filter(Transaction transaction) {
        guards.forEach(guard -> guard.evaluate(transaction)
                .ifPresent(violation -> {
                    Alert alert = new Alert(
                            guard.getRule(),
                            violation.severity(),
                            violation.message(),
                            Instant.now()
                    );
                    transaction.addAlert(alert);
                })
        );
    }
}
