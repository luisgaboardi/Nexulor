package com.nexulor.wallet.infrastructure.fraud;

import com.nexulor.wallet.application.port.FraudEvaluationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.math.BigInteger;
import java.util.UUID;

/**
 * Local/offline fallback used when the wallet runs without the fraud stack
 * (default profile). Production wiring is {@link GrpcFraudClient} under the
 * {@code grpc-fraud} profile; approvals here are logged loudly so the mode
 * is never silent.
 */
@Configuration
@Profile("!grpc-fraud")
public class NoopFraudAdapter implements FraudEvaluationPort {

    private static final Logger log = LoggerFactory.getLogger(NoopFraudAdapter.class);

    public NoopFraudAdapter() {
        log.warn("NOOP fraud adapter active — every transfer will be approved without risk checks. " +
                "Start with grpc-fraud profile and a reachable fraud-service in production-like runs.");
    }

    @Override
    public Decision evaluate(UUID transferId,
                             UUID sourceWalletId,
                             UUID destinationWalletId,
                             BigInteger amountMinor,
                             String currencyCode) {
        return Decision.APPROVE;
    }
}
