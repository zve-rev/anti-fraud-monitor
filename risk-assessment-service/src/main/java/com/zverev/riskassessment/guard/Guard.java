package com.zverev.riskassessment.guard;

import com.zverev.shared.entity.Transaction;
import com.zverev.shared.entity.enums.Rule;
import com.zverev.riskassessment.dto.GuardViolation;

import java.util.Optional;

public interface Guard {
    Optional<GuardViolation> evaluate(Transaction transaction);
    Rule getRule();
}
