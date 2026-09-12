package com.nexulor.fraud.grpc;

import com.nexulor.fraud.audit.AuditWriter;
import com.nexulor.fraud.audit.FraudEvaluationAuditDocument;
import com.nexulor.fraud.rules.AmountCeilingRule;
import com.nexulor.fraud.rules.FraudRulesEngine;
import com.nexulor.fraud.rules.SelfTransferRule;
import com.nexulor.grpc.fraud.v1.Decision;
import com.nexulor.grpc.fraud.v1.EvaluateTransactionRequest;
import com.nexulor.grpc.fraud.v1.EvaluateTransactionResponse;
import com.nexulor.grpc.fraud.v1.FraudEvaluationServiceGrpc;
import io.grpc.BindableService;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * In-process gRPC integration test: real protobuf serialization, real server
 * dispatch and real status codes — no network, no MongoDB (audit records are
 * captured by an in-memory AuditWriter fake).
 */
class FraudEvaluationGrpcIntegrationTest {

    private final BlockingQueue<FraudEvaluationAuditDocument> auditCapture = new LinkedBlockingQueue<>();

    private Server server;
    private ManagedChannel channel;
    private FraudEvaluationServiceGrpc.FraudEvaluationServiceBlockingStub stub;

    @BeforeEach
    void startServer() throws IOException {
        AuditWriter capturingWriter = auditCapture::add;

        FraudRulesEngine engine = new FraudRulesEngine(List.of(
                new SelfTransferRule(),
                new AmountCeilingRule(new BigInteger("10000000"))));
        BindableService grpcService = new FraudEvaluationGrpcService(engine, capturingWriter);

        String serverName = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(grpcService)
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(serverName)
                .directExecutor()
                .build();
        stub = FraudEvaluationServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void stopServer() {
        if (channel != null) channel.shutdownNow();
        if (server != null) server.shutdownNow();
    }

    private static EvaluateTransactionRequest request(String amountMinor) {
        return EvaluateTransactionRequest.newBuilder()
                .setTransferId(UUID.randomUUID().toString())
                .setSourceWalletId("00000000-0000-0000-0000-000000000001")
                .setDestinationWalletId("00000000-0000-0000-0000-000000000002")
                .setAmountMinor(amountMinor)
                .setCurrencyCode("BRL")
                .build();
    }

    @Test
    void roundTripsApprovalOverTheWire() {
        EvaluateTransactionResponse response = stub.evaluateTransaction(request("2500"));

        assertEquals(Decision.APPROVE, response.getDecision());
        assertEquals("", response.getRuleId());
    }

    @Test
    void roundTripsRejectionWithRuleIdOverTheWire() {
        EvaluateTransactionResponse response = stub.evaluateTransaction(request("99999999"));

        assertEquals(Decision.REJECT, response.getDecision());
        assertEquals("R1-amount-ceiling", response.getRuleId());
        assertEquals("amount 99999999 exceeds single-transfer ceiling 10000000 (minor units)",
                response.getReason());
    }

    @Test
    void malformedTransferIdSurfacesAsInvalidArgument() {
        StatusRuntimeException error = assertThrows(StatusRuntimeException.class,
                () -> stub.evaluateTransaction(request("2500").toBuilder()
                        .setTransferId("not-a-uuid")
                        .build()));

        assertEquals(Status.Code.INVALID_ARGUMENT, error.getStatus().getCode());
    }

    @Test
    void zeroAmountSurfacesAsInvalidArgument() {
        StatusRuntimeException error = assertThrows(StatusRuntimeException.class,
                () -> stub.evaluateTransaction(request("0")));

        assertEquals(Status.Code.INVALID_ARGUMENT, error.getStatus().getCode());
    }

    @Test
    void selfTransferSurfacesAsRejectionFromR3() {
        String sameWallet = "00000000-0000-0000-0000-000000000001";
        EvaluateTransactionResponse response = stub.evaluateTransaction(request("100").toBuilder()
                .setDestinationWalletId(sameWallet)
                .build());

        assertEquals(Decision.REJECT, response.getDecision());
        assertEquals("R3-self-transfer", response.getRuleId());
    }

    @Test
    void auditRecordIsCapturedPerEvaluation() throws InterruptedException {
        stub.evaluateTransaction(request("2500"));

        FraudEvaluationAuditDocument document = auditCapture.take(); // blocks until written
        assertEquals(Decision.APPROVE.name(), document.decision());
        assertEquals("2500", document.amountMinor());
        assertTrue(document.latencyMillis() >= 0);
        assertFalse(document.evaluatedAt() == null);
    }
}
