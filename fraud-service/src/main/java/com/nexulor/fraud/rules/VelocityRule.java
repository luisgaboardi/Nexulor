package com.nexulor.fraud.rules;

import com.nexulor.fraud.domain.FraudDecision;
import com.nexulor.fraud.domain.FraudEvaluationResult;
import com.nexulor.fraud.domain.FraudRule;
import com.nexulor.fraud.domain.TransactionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * R2: velocity heuristic — rejects when the same source wallet accumulates
 * more than {@code maxTransfers} debits within the rolling window.
 *
 * <p>Phase 3: the counter lives behind {@link VelocityCounterPort}. The
 * production wiring is the cross-instance Redis counter (profile
 * {@code redis-velocity}); the local Caffeine counter remains the default for
 * standalone runs. If the counter infrastructure fails, the rule fails open —
 * a broken counter must not reject every transfer, and R1/R3 still run.</p>
 */
public class VelocityRule implements FraudRule {

    public static final String ID = "R2-velocity-source";

    private static final Logger log = LoggerFactory.getLogger(VelocityRule.class);

    private final int maxTransfersPerWindow;
    private final Duration window;
    private final VelocityCounterPort counter;

    public VelocityRule(int maxTransfersPerWindow, Duration window, VelocityCounterPort counter) {
        if (maxTransfersPerWindow <= 0) {
            throw new IllegalArgumentException("maxTransfersPerWindow must be positive");
        }
        this.maxTransfersPerWindow = maxTransfersPerWindow;
        this.window = window;
        this.counter = counter;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public FraudEvaluationResult evaluate(TransactionContext context) {
        String key = context.sourceWalletId().toString();
        int seen;
        try {
            seen = counter.incrementAndGet(key, window);
        } catch (RuntimeException e) {
            log.warn("velocity counter unavailable ({}); failing open for rule {}", e.toString(), ID);
            return null;
        }
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
