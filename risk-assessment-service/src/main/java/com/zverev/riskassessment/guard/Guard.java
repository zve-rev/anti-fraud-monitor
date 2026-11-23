package com.zverev.riskassessment.guard;

import com.zverev.entity.Transaction;
import com.zverev.entity.enums.Rule;
import com.zverev.riskassessment.dto.GuardViolation;

import java.util.Optional;

public interface Guard {
    Optional<GuardViolation> evaluate(Transaction transaction);
    Rule getRule();
}
