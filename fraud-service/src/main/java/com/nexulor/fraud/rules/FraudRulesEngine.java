package com.nexulor.fraud.rules;

import com.nexulor.fraud.domain.FraudEvaluationResult;
import com.nexulor.fraud.domain.FraudRule;
import com.nexulor.fraud.domain.TransactionContext;

import java.util.List;

/**
 * Ordered synchronous rules pipeline. First rule to fire wins.
 */
public class FraudRulesEngine {

    private final List<FraudRule> rules;

    public FraudRulesEngine(List<FraudRule> rules) {
        this.rules = List.copyOf(rules);
    }

    public FraudEvaluationResult evaluate(TransactionContext context) {
        for (FraudRule rule : rules) {
            FraudEvaluationResult result = rule.evaluate(context);
            if (result != null) {
                return result;
            }
        }
        return FraudEvaluationResult.approve();
    }
}
