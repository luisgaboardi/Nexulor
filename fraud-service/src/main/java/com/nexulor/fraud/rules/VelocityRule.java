package com.nexulor.fraud.rules;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nexulor.fraud.domain.FraudDecision;
import com.nexulor.fraud.domain.FraudEvaluationResult;
import com.nexulor.fraud.domain.FraudRule;
import com.nexulor.fraud.domain.TransactionContext;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * R2: velocity heuristic — rejects when the same source wallet accumulates
 * more than {@code maxTransfers} debits within the rolling window.
 *
 * <p>Counters are per-instance (Caffeine) in Phase 2; the cross-instance
 * Redis version arrives with Phase 3, so this rule is a best-effort signal
 * until then (documented in ADR-005).</p>
 */
public class VelocityRule implements FraudRule {

    public static final String ID = "R2-velocity-source";

    private final int maxTransfersPerWindow;
    private final Cache<String, AtomicInteger> counters;

    public VelocityRule(int maxTransfersPerWindow, Duration window) {
        if (maxTransfersPerWindow <= 0) {
            throw new IllegalArgumentException("maxTransfersPerWindow must be positive");
        }
        this.maxTransfersPerWindow = maxTransfersPerWindow;
        this.counters = Caffeine.newBuilder()
                .expireAfterWrite(window)
                .build();
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public FraudEvaluationResult evaluate(TransactionContext context) {
        String key = context.sourceWalletId().toString();
        AtomicInteger counter = counters.get(key, k -> new AtomicInteger());
        int seen = counter.incrementAndGet();
        if (seen > maxTransfersPerWindow) {
            return new FraudEvaluationResult(
                    FraudDecision.REJECT,
                    ID,
                    "source wallet exceeded velocity limit: %d transfers within window (limit %d)"
                            .formatted(seen, maxTransfersPerWindow));
        }
        return null;
    }
}
