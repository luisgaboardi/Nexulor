package com.nexulor.fraud.rules;

import com.nexulor.fraud.domain.FraudDecision;
import com.nexulor.fraud.domain.FraudEvaluationResult;
import com.nexulor.fraud.domain.TransactionContext;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class FraudRulesEngineTest {

    private static TransactionContext context(String amountMinor) {
        return new TransactionContext(
                UUID.randomUUID(),
                UUID.fromString("00000000-0000-0000-0000-00000000000a"),
                UUID.fromString("00000000-0000-0000-0000-00000000000b"),
                new BigInteger(amountMinor),
                "BRL");
    }

    @Test
    void selfTransferRuleFiresFirstAndRejects() {
        UUID walletId = UUID.fromString("00000000-0000-0000-0000-00000000000a");
        TransactionContext selfTransfer = new TransactionContext(
                UUID.randomUUID(), walletId, walletId, BigInteger.ONE, "BRL");

        FraudRulesEngine engine = new FraudRulesEngine(List.of(
                new SelfTransferRule(),
                new AmountCeilingRule("10000000"),
                new VelocityRule(5, Duration.ofSeconds(60), new LocalVelocityCounter())));

        FraudEvaluationResult result = engine.evaluate(selfTransfer);

        assertEquals(FraudDecision.REJECT, result.decision());
        assertEquals(SelfTransferRule.ID, result.ruleId());
    }

    @Test
    void amountCeilingRuleRejectsAboveLimit() {
        FraudRulesEngine engine = new FraudRulesEngine(List.of(new AmountCeilingRule("100000")));

        FraudEvaluationResult result = engine.evaluate(context("100001"));

        assertEquals(FraudDecision.REJECT, result.decision());
        assertEquals(AmountCeilingRule.ID, result.ruleId());
    }

    @Test
    void amountCeilingRulePassesAtExactLimit() {
        FraudRulesEngine engine = new FraudRulesEngine(List.of(new AmountCeilingRule("100000")));

        assertEquals(FraudDecision.APPROVE, engine.evaluate(context("100000")).decision());
    }

    @Test
    void velocityRuleAllowsUpToLimitThenRejects() {
        VelocityRule rule = new VelocityRule(2, Duration.ofSeconds(60), new LocalVelocityCounter());
        FraudRulesEngine engine = new FraudRulesEngine(List.of(rule));

        UUID sourceId = UUID.randomUUID();
        TransactionContext base = new TransactionContext(
                UUID.randomUUID(),
                sourceId,
                UUID.randomUUID(),
                BigInteger.ONE,
                "BRL");

        assertEquals(FraudDecision.APPROVE, engine.evaluate(base).decision());
        assertEquals(FraudDecision.APPROVE, engine.evaluate(base).decision());
        FraudEvaluationResult third = engine.evaluate(base);

        assertEquals(FraudDecision.REJECT, third.decision());
        assertEquals(VelocityRule.ID, third.ruleId());
    }

    @Test
    void velocityRuleCountersArePerWallet() {
        VelocityRule rule = new VelocityRule(1, Duration.ofSeconds(60), new LocalVelocityCounter());
        FraudRulesEngine engine = new FraudRulesEngine(List.of(rule));

        TransactionContext fromA = new TransactionContext(
                UUID.randomUUID(),
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.randomUUID(),
                BigInteger.ONE,
                "BRL");
        TransactionContext fromB = new TransactionContext(
                UUID.randomUUID(),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                UUID.randomUUID(),
                BigInteger.ONE,
                "BRL");

        assertEquals(FraudDecision.APPROVE, engine.evaluate(fromA).decision());
        assertEquals(FraudDecision.APPROVE, engine.evaluate(fromB).decision());
        assertSame(FraudDecision.REJECT, engine.evaluate(fromA).decision());
    }

    @Test
    void engineApprovesWhenNoRuleFires() {
        FraudRulesEngine engine = new FraudRulesEngine(List.of(
                new SelfTransferRule(),
                new AmountCeilingRule("10000000")));

        FraudEvaluationResult result = engine.evaluate(context("2500"));

        assertEquals(FraudDecision.APPROVE, result.decision());
        assertEquals("", result.ruleId());
    }

    @Test
    void windowExpiryResetsVelocityCounters() throws InterruptedException {
        VelocityRule rule = new VelocityRule(1, Duration.ofMillis(50), new LocalVelocityCounter());

        TransactionContext tx = new TransactionContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                BigInteger.ONE,
                "BRL");

        assertNull(rule.evaluate(tx));
        assertEquals(FraudDecision.REJECT, rule.evaluate(tx).decision());
        Thread.sleep(120); // window expires
        assertNull(rule.evaluate(tx));
    }
}
