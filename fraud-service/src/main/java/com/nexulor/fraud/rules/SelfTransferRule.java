package com.nexulor.fraud.rules;

import com.nexulor.fraud.domain.FraudDecision;
import com.nexulor.fraud.domain.FraudEvaluationResult;
import com.nexulor.fraud.domain.FraudRule;
import com.nexulor.fraud.domain.TransactionContext;

/**
 * R3: self-transfer defense. The wallet domain also enforces this, so the
 * rule is a redundant tripwire that flags suspicious client behavior.
 */
public class SelfTransferRule implements FraudRule {

    public static final String ID = "R3-self-transfer";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public FraudEvaluationResult evaluate(TransactionContext context) {
        if (context.sourceWalletId().equals(context.destinationWalletId())) {
            return new FraudEvaluationResult(
                    FraudDecision.REJECT,
                    ID,
                    "source and destination wallets must differ");
        }
        return null;
    }
}
