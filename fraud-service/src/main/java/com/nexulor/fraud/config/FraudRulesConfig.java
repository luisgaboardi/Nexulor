package com.nexulor.fraud.config;

import com.nexulor.fraud.rules.AmountCeilingRule;
import com.nexulor.fraud.rules.FraudRulesEngine;
import com.nexulor.fraud.rules.SelfTransferRule;
import com.nexulor.fraud.rules.VelocityCounterPort;
import com.nexulor.fraud.rules.VelocityRule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigInteger;
import java.time.Duration;
import java.util.List;

/**
 * Explicit rule ordering: cheapest checks first (R3), then value ceiling (R1),
 * then stateful velocity (R2). The velocity counter is the cross-instance
 * Redis port under the {@code redis-velocity} profile, Caffeine locally.
 */
@Configuration
public class FraudRulesConfig {

    @Bean
    public FraudRulesEngine fraudRulesEngine(
            @Value("${fraud.rules.amount-ceiling-minor}") String amountCeilingMinor,
            @Value("${fraud.rules.velocity-max-transfers}") int velocityMaxTransfers,
            @Value("${fraud.rules.velocity-window}") Duration velocityWindow,
            VelocityCounterPort velocityCounter) {
        return new FraudRulesEngine(List.of(
                new SelfTransferRule(),
                new AmountCeilingRule(new BigInteger(amountCeilingMinor)),
                new VelocityRule(velocityMaxTransfers, velocityWindow, velocityCounter)));
    }
}
