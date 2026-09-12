package com.nexulor.fraud.domain;

/**
 * A single synchronous fraud rule. Implementations must be fast and
 * non-blocking; the wallet service enforces a 300ms deadline on the call.
 */
public interface FraudRule {

    /** Stable identifier used in audit records and metrics. */
    String id();

    /** Returns a rejection/review result when the rule fires, or null to pass. */
    FraudEvaluationResult evaluate(TransactionContext context);
}
