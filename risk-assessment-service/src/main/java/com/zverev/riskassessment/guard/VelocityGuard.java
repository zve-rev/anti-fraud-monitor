package com.zverev.riskassessment.guard;

import com.zverev.shared.entity.Transaction;
import com.zverev.shared.entity.enums.AlertSeverity;
import com.zverev.shared.entity.enums.Rule;
import com.zverev.riskassessment.dto.GuardViolation;
import com.zverev.shared.util.CardMasker;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class VelocityGuard implements Guard {

    private static final String REDIS_KEY_PREFIX = "velocity:card:";
    private static final Duration WINDOW = Duration.ofMinutes(5);
    private static final int MAX_IN_WINDOW = 5;

    private final StringRedisTemplate redisTemplate;

    @Override
    public Optional<GuardViolation> evaluate(Transaction transaction) {
        String key = REDIS_KEY_PREFIX + transaction.getCardToken();
        String member = transaction.getExternalId().toString();
        long now = System.currentTimeMillis();

        ZSetOperations<String, String> zSet = redisTemplate.opsForZSet();
        zSet.removeRangeByScore(key, 0, now - WINDOW.toMillis());
        zSet.add(key, member, now);

        redisTemplate.expire(key, WINDOW);

        Long currentCount = zSet.size(key);

        if (currentCount == null) {
            return Optional.empty();
        }

        String maskedToken = CardMasker.mask(transaction.getCardToken());

        if (currentCount > MAX_IN_WINDOW) {
            return Optional.of(
                    new GuardViolation(
                            AlertSeverity.CRITICAL,
                            String.format(
                                    "Velocity exceeded for card %s: %d transactions in last %d minutes",
                                    maskedToken,
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