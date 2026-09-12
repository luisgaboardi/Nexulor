package com.nexulor.fraud.grpc;

import com.nexulor.fraud.audit.AuditWriter;
import com.nexulor.fraud.audit.FraudEvaluationAuditDocument;
import com.nexulor.fraud.domain.FraudEvaluationResult;
import com.nexulor.fraud.domain.TransactionContext;
import com.nexulor.fraud.rules.FraudRulesEngine;
import com.nexulor.grpc.fraud.v1.EvaluateTransactionRequest;
import com.nexulor.grpc.fraud.v1.EvaluateTransactionResponse;
import com.nexulor.grpc.fraud.v1.FraudEvaluationServiceGrpc;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;

@GrpcService
public class FraudEvaluationGrpcService extends FraudEvaluationServiceGrpc.FraudEvaluationServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(FraudEvaluationGrpcService.class);

    private final FraudRulesEngine rulesEngine;
    private final AuditWriter auditWriter;

    public FraudEvaluationGrpcService(FraudRulesEngine rulesEngine, AuditWriter auditWriter) {
        this.rulesEngine = rulesEngine;
        this.auditWriter = auditWriter;
    }

    @Override
    public void evaluateTransaction(
            EvaluateTransactionRequest request,
            StreamObserver<EvaluateTransactionResponse> responseObserver) {

        long start = System.nanoTime();
        try {
            TransactionContext context = new TransactionContext(
                    UUID.fromString(request.getTransferId()),
                    UUID.fromString(request.getSourceWalletId()),
                    UUID.fromString(request.getDestinationWalletId()),
                    new BigInteger(request.getAmountMinor()),
                    request.getCurrencyCode());

            FraudEvaluationResult result = rulesEngine.evaluate(context);
            long latencyMillis = (System.nanoTime() - start) / 1_000_000;

            auditWriter.enqueue(new FraudEvaluationAuditDocument(
                    null,
                    request.getTransferId(),
                    request.getSourceWalletId(),
                    request.getDestinationWalletId(),
                    request.getAmountMinor(),
                    request.getCurrencyCode(),
                    result.decision().name(),
                    result.ruleId(),
                    result.reason(),
                    latencyMillis,
                    Instant.now()));

            responseObserver.onNext(EvaluateTransactionResponse.newBuilder()
                    .setDecision(result.decision().toWire())
                    .setRuleId(result.ruleId())
                    .setReason(result.reason())
                    .build());
            responseObserver.onCompleted();
        } catch (IllegalArgumentException e) {
            log.warn("invalid fraud evaluation request: {}", e.getMessage());
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (RuntimeException e) {
            log.error("fraud evaluation failed", e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("evaluation failed")
                    .asRuntimeException());
        }
    }
}
