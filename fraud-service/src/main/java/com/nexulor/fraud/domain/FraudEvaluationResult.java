package com.nexulor.fraud.domain;

/**
 * Result of a fraud evaluation: the decision, the rule that produced it
 * (empty when none fired) and a human-readable reason.
 */
public record FraudEvaluationResult(FraudDecision decision, String ruleId, String reason) {

    public FraudEvaluationResult {
        if (decision == null) {
            throw new IllegalArgumentException("decision is required");
        }
        ruleId = ruleId == null ? "" : ruleId;
        reason = reason == null ? "" : reason;
    }

    public static FraudEvaluationResult approve() {
        return new FraudEvaluationResult(FraudDecision.APPROVE, "", "no rule triggered");
    }
}
