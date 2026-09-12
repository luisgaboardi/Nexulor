package com.nexulor.fraud.rules;

import com.nexulor.fraud.domain.FraudDecision;
import com.nexulor.fraud.domain.FraudEvaluationResult;
import com.nexulor.fraud.domain.FraudRule;
import com.nexulor.fraud.domain.TransactionContext;

import java.math.BigInteger;
import java.util.Objects;

/**
 * R1: rejects single transfers above a configurable ceiling
 * (amount in minor units, e.g. 10000000 = 100,000.00 BRL).
 */
public class AmountCeilingRule implements FraudRule {

    public static final String ID = "R1-amount-ceiling";

    private final BigInteger ceilingMinor;

    public AmountCeilingRule(BigInteger ceilingMinor) {
        Objects.requireNonNull(ceilingMinor, "ceilingMinor");
        if (ceilingMinor.signum() <= 0) {
            throw new IllegalArgumentException("ceilingMinor must be positive");
        }
        this.ceilingMinor = ceilingMinor;
    }

    public AmountCeilingRule(String ceilingMinor) {
        this(new BigInteger(ceilingMinor));
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public FraudEvaluationResult evaluate(TransactionContext context) {
        if (context.amountMinor().compareTo(ceilingMinor) > 0) {
            return new FraudEvaluationResult(
                    FraudDecision.REJECT,
                    ID,
                    "amount %s exceeds single-transfer ceiling %s (minor units)"
                            .formatted(context.amountMinor(), ceilingMinor));
        }
        return null;
    }
}
