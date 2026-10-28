package com.zverev.scoring.guard;

import com.zverev.entity.Transaction;
import com.zverev.entity.enums.Rule;
import com.zverev.scoring.dto.GuardViolation;

import java.util.Optional;

public interface Guard {
    Optional<GuardViolation> evaluate(Transaction transaction);
    Rule getRule();
}
