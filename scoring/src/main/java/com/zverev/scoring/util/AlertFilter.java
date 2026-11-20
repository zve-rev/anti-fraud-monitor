package com.zverev.scoring.util;

import com.zverev.entity.Alert;
import com.zverev.entity.Transaction;
import com.zverev.scoring.guard.Guard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AlertFilter {

    private final List<Guard> guards;

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
