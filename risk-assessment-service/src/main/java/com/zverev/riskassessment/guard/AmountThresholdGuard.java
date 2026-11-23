package com.zverev.riskassessment.guard;

import com.zverev.shared.entity.Transaction;
import com.zverev.shared.entity.enums.AlertSeverity;
import com.zverev.shared.entity.enums.CurrencyCode;
import com.zverev.shared.entity.enums.Rule;
import com.zverev.riskassessment.dto.GuardViolation;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

@Component
public class AmountThresholdGuard implements Guard {

    private static final Map<CurrencyCode, BigDecimal> THRESHOLDS = Map.of(
            CurrencyCode.EUR, new BigDecimal("5000"),
            CurrencyCode.CHF, new BigDecimal("5000"),
            CurrencyCode.CAD, new BigDecimal("7000"),
            CurrencyCode.AUD, new BigDecimal("8000"),
            CurrencyCode.BRL, new BigDecimal("30000"),
            CurrencyCode.JPY, new BigDecimal("800000")
    );

    @Override
    public Optional<GuardViolation> evaluate(Transaction transaction) {
        BigDecimal threshold = THRESHOLDS.get(transaction.getCurrencyCode());

        if (threshold != null && transaction.getAmount().compareTo(threshold) > 0) {
            return Optional.of(
                    new GuardViolation(
                            AlertSeverity.MEDIUM,
                            String.format("Amount %s %s exceeds threshold %s %s",
                                    transaction.getAmount(),
                                    transaction.getCurrencyCode(),
                                    threshold,
                                    transaction.getCurrencyCode())
                    )
            );
        }

        return Optional.empty();
    }

    @Override
    public Rule getRule() {
        return Rule.AMOUNT_THRESHOLD;
    }
}
