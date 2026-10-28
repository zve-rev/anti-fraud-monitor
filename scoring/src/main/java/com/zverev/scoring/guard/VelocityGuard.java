package com.zverev.scoring.guard;

import com.zverev.entity.Transaction;
import com.zverev.entity.enums.AlertSeverity;
import com.zverev.entity.enums.Rule;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class VelocityGuard implements Guard {

    private static final Duration WINDOW = Duration.ofMinutes(5);
    private static final int MAX_IN_WINDOW = 5;
    private static final String KEY_PREFIX = "velocity:card:";

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public Optional<GuardViolation> evaluate(Transaction transaction) {
        String key = KEY_PREFIX + transaction.getCardToken();

        Long currentCount = stringRedisTemplate.opsForValue().increment(key);

        if (currentCount == 1L) {
            stringRedisTemplate.expire(key, WINDOW);
        }

        if (currentCount > MAX_IN_WINDOW) {
            return Optional.of(
                    new GuardViolation(
                            AlertSeverity.CRITICAL,
                            String.format(
                                    "Velocity exceeded for card %s: %d transactions in last %d minutes",
                                    transaction.getCardToken(),
                                    currentCount,
                                    WINDOW.toMinutes()
                            )
                    )
            );
        }

        return Optional.empty();
    }

    @Override
    public Rule getRule() {
        return Rule.VELOCITY;
    }
}