package com.nexulor.fraud.rules;

import java.time.Duration;

/**
 * Port for the cross-instance velocity counter (R2). Implementations must be
 * safe under concurrent evaluation from every fraud-service instance.
 */
public interface VelocityCounterPort {

    /**
     * Increments the counter for {@code sourceWalletId} and returns the
     * current value within the rolling window.
     */
    int incrementAndGet(String sourceWalletId, Duration window);
}
