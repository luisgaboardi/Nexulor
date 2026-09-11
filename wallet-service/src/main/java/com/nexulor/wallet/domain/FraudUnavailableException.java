package com.nexulor.wallet.domain;

/**
 * Raised when the fraud service is unreachable (after retries / open circuit
 * breaker) and the system refuses to move money without a risk opinion
 * (fail-closed policy, ADR-005).
 */
public class FraudUnavailableException extends DomainException {

    public FraudUnavailableException(String message) {
        super(message);
    }
}
