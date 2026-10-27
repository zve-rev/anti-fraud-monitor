package com.zverev.guard;

import com.zverev.dto.GuardViolation;
import com.zverev.entity.Transaction;
import com.zverev.entity.enums.AlertSeverity;
import com.zverev.entity.enums.Rule;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

@Component
public class HighRiskMerchantGuard implements Guard {

    private static final Set<String> HIGH_RISK = Set.of(
            "Casino", "Online Poker", "Crypto Exchange",
            "Gambling", "Wire Transfer"
    );

    @Override
    public Optional<GuardViolation> evaluate(Transaction transaction) {
        if (HIGH_RISK.contains(transaction.getMerchantName())) {
            return Optional.of(
                    new GuardViolation(
                            AlertSeverity.HIGH,
                            String.format(
                                    "High-risk merchant category: %s",
                                    transaction.getMerchantName()
                            )
                    )
            );
        }

        return Optional.empty();
    }

    @Override
    public Rule getRule() {
        return Rule.HIGH_RISK_MERCHANT;
    }
}
