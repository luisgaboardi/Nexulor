package com.nexulor.wallet.infrastructure.fraud;

import com.nexulor.grpc.fraud.v1.EvaluateTransactionRequest;
import com.nexulor.grpc.fraud.v1.EvaluateTransactionResponse;
import com.nexulor.grpc.fraud.v1.FraudEvaluationServiceGrpc;
import com.nexulor.wallet.application.port.FraudEvaluationPort;
import com.nexulor.wallet.domain.FraudUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * gRPC adapter for the fraud evaluation port (profile {@code grpc-fraud}).
 *
 * <p>Resilience chain: per-call deadline of 300ms (orTimeout + gRPC deadline)
 * -> Retry (3 attempts, transport errors only) -> CircuitBreaker (see
 * application.yml). Any failure after the chain maps to
 * {@link FraudUnavailableException} — the transfer path is fail-closed (ADR-005).</p>
 */
@Component
@Profile("grpc-fraud")
public class GrpcFraudClient implements FraudEvaluationPort {

    private static final Logger log = LoggerFactory.getLogger(GrpcFraudClient.class);

    private final long deadlineMillis;

    private final ExecutorService virtualThreads = Executors.newVirtualThreadPerTaskExecutor();

    public GrpcFraudClient(@Value("${fraud.client.deadline-millis:300}") long deadlineMillis) {
        this.deadlineMillis = deadlineMillis;
    }

    @GrpcClient("fraudService")
    private FraudEvaluationServiceGrpc.FraudEvaluationServiceBlockingStub fraudStub;

    @Override
    @Retry(name = "fraud")
    @CircuitBreaker(name = "fraud", fallbackMethod = "fraudUnavailable")
    public Decision evaluate(UUID transferId,
                             UUID sourceWalletId,
                             UUID destinationWalletId,
                             BigInteger amountMinor,
                             String currencyCode) {
        return CompletableFuture.supplyAsync(() -> callRemote(
                transferId, sourceWalletId, destinationWalletId, amountMinor, currencyCode), virtualThreads)
                .orTimeout(deadlineMillis, TimeUnit.MILLISECONDS)
                .join();
    }

    private Decision callRemote(UUID transferId,
                                UUID sourceWalletId,
                                UUID destinationWalletId,
                                BigInteger amountMinor,
                                String currencyCode) {
        EvaluateTransactionRequest request = EvaluateTransactionRequest.newBuilder()
                .setTransferId(transferId.toString())
                .setSourceWalletId(sourceWalletId.toString())
                .setDestinationWalletId(destinationWalletId.toString())
                .setAmountMinor(amountMinor.toString())
                .setCurrencyCode(currencyCode)
                .build();
        EvaluateTransactionResponse response = fraudStub
                .withDeadlineAfter(deadlineMillis, TimeUnit.MILLISECONDS)
                .evaluateTransaction(request);
        return map(response);
    }

    private Decision map(EvaluateTransactionResponse response) {
        return switch (response.getDecision()) {
            case APPROVE -> Decision.APPROVE;
            case REJECT -> Decision.REJECT;
            default -> {
                log.info("fraud returned REVIEW for transfer {}; treated as APPROVE in Phase 2 (ADR-005)",
                        response.getRuleId());
                yield Decision.REVIEW;
            }
        };
    }

    @SuppressWarnings("unused")
    private Decision fraudUnavailable(UUID transferId,
                                      UUID sourceWalletId,
                                      UUID destinationWalletId,
                                      BigInteger amountMinor,
                                      String currencyCode,
                                      Throwable cause) {
        log.warn("fraud evaluation unavailable ({}); failing closed", cause.toString());
        throw new FraudUnavailableException(
                "fraud service unavailable; transfer refused (fail-closed)");
    }
}
