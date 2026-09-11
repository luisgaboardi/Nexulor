package com.nexulor.wallet.application.port;

import java.math.BigInteger;
import java.util.UUID;

/**
 * Driven port for synchronous fraud evaluation on the transfer path.
 */
public interface FraudEvaluationPort {

    /**
     * @return APPROVE when the transfer may proceed.
     */
    Decision evaluate(UUID transferId,
                      UUID sourceWalletId,
                      UUID destinationWalletId,
                      BigInteger amountMinor,
                      String currencyCode);

    enum Decision {
        APPROVE,
        REJECT,
        REVIEW
    }
}
