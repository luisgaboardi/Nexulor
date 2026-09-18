package com.nexulor.fraud.rules;

import com.nexulor.fraud.domain.FraudDecision;
import com.nexulor.fraud.domain.FraudEvaluationResult;
import com.nexulor.fraud.domain.TransactionContext;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VelocityRuleTest {

    private final Map<String, AtomicInteger> counters = new HashMap<>();

    @Test
    void rejectsWhenCounterExceedsLimit() {
        VelocityRule rule = new VelocityRule(2, Duration.ofSeconds(60), countingPort());

        assertNull(rule.evaluate(context()));
        assertNull(rule.evaluate(context()));
        FraudEvaluationResult result = rule.evaluate(context());

        assertEquals(FraudDecision.REJECT, result.decision());
        assertEquals(VelocityRule.ID, result.ruleId());
    }

    @Test
    void failsOpenWhenCounterInfrastructureFails() {
        VelocityRule rule = new VelocityRule(5, Duration.ofSeconds(60),
                (walletId, window) -> {
                    throw new IllegalStateException("redis down");
                });

        assertNull(rule.evaluate(context()));
    }

    @Test
    void rejectsNonPositiveLimit() {
        assertThrows(IllegalArgumentException.class,
                () -> new VelocityRule(0, Duration.ofSeconds(60), countingPort()));
    }

    private VelocityCounterPort countingPort() {
        return (walletId, window) -> counters
                .computeIfAbsent(walletId, k -> new AtomicInteger())
                .incrementAndGet();
    }

    private TransactionContext context() {
        return new TransactionContext(
                java.util.UUID.randomUUID(),
                SOURCE_WALLET,
                java.util.UUID.randomUUID(),
                java.math.BigInteger.ONE,
                "BRL");
    }

    private static final java.util.UUID SOURCE_WALLET = java.util.UUID.randomUUID();
}
