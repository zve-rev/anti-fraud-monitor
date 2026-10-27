package com.zverev.guard;

import com.zverev.dto.GuardViolation;
import com.zverev.entity.Transaction;
import com.zverev.entity.enums.Rule;

import java.util.Optional;

public interface Guard {
    Optional<GuardViolation> evaluate(Transaction transaction);
    Rule getRule();
}
