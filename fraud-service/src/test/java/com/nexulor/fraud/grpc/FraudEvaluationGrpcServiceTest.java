package com.nexulor.fraud.grpc;

import com.nexulor.fraud.audit.AsyncAuditWriter;
import com.nexulor.fraud.audit.FraudEvaluationAuditDocument;
import com.nexulor.fraud.domain.FraudDecision;
import com.nexulor.fraud.rules.FraudRulesEngine;
import com.nexulor.grpc.fraud.v1.EvaluateTransactionRequest;
import com.nexulor.grpc.fraud.v1.EvaluateTransactionResponse;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FraudEvaluationGrpcServiceTest {

    @Mock
    private AsyncAuditWriter auditWriter;

    @Mock
    private StreamObserver<EvaluateTransactionResponse> responseObserver;

    private FraudEvaluationGrpcService service;

    @BeforeEach
    void setUp() {
        FraudRulesEngine engine = new FraudRulesEngine(List.of(
                new com.nexulor.fraud.rules.SelfTransferRule(),
                new com.nexulor.fraud.rules.AmountCeilingRule("10000000")));
        service = new FraudEvaluationGrpcService(engine, auditWriter);
    }

    private static EvaluateTransactionRequest validRequest() {
        return EvaluateTransactionRequest.newBuilder()
                .setTransferId("11111111-1111-1111-1111-111111111111")
                .setSourceWalletId("00000000-0000-0000-0000-000000000001")
                .setDestinationWalletId("00000000-0000-0000-0000-000000000002")
                .setAmountMinor("2500")
                .setCurrencyCode("BRL")
                .build();
    }

    @Test
    void approvesLegitimateTransactionAndEnqueuesAudit() {
        AtomicReference<EvaluateTransactionResponse> reply = new AtomicReference<>();
        doAnswer(invocation -> {
            reply.set(invocation.getArgument(0));
            return null;
        }).when(responseObserver).onNext(any());

        service.evaluateTransaction(validRequest(), responseObserver);

        assertNotNull(reply.get());
        assertEquals(FraudDecision.APPROVE.name(), reply.get().getDecision().name());
        verify(responseObserver).onCompleted();
        verify(auditWriter).enqueue(any(FraudEvaluationAuditDocument.class));
    }

    @Test
    void rejectsAmountAboveCeilingWithRuleId() {
        AtomicReference<EvaluateTransactionResponse> reply = new AtomicReference<>();
        doAnswer(invocation -> {
            reply.set(invocation.getArgument(0));
            return null;
        }).when(responseObserver).onNext(any());

        service.evaluateTransaction(
                validRequest().toBuilder().setAmountMinor("99999999").build(),
                responseObserver);

        assertEquals(FraudDecision.REJECT.name(), reply.get().getDecision().name());
        assertEquals("R1-amount-ceiling", reply.get().getRuleId());
    }

    @Test
    void invalidMalformedIdsMapToInvalidArgument() {
        EvaluateTransactionRequest bad = validRequest().toBuilder()
                .setTransferId("not-a-uuid")
                .build();

        service.evaluateTransaction(bad, responseObserver);

        ArgumentCaptor<StatusRuntimeException> error = ArgumentCaptor.forClass(StatusRuntimeException.class);
        verify(responseObserver).onError(error.capture());
        assertEquals(Status.Code.INVALID_ARGUMENT, error.getValue().getStatus().getCode());
    }

    @Test
    void nonPositiveAmountMapsToInvalidArgument() {
        EvaluateTransactionRequest bad = validRequest().toBuilder()
                .setAmountMinor("0")
                .build();

        service.evaluateTransaction(bad, responseObserver);

        ArgumentCaptor<StatusRuntimeException> error = ArgumentCaptor.forClass(StatusRuntimeException.class);
        verify(responseObserver).onError(error.capture());
        assertEquals(Status.Code.INVALID_ARGUMENT, error.getValue().getStatus().getCode());
    }

    @Test
    void auditRecordCarriesRequestData() {
        service.evaluateTransaction(validRequest(), responseObserver);

        ArgumentCaptor<FraudEvaluationAuditDocument> audit =
                ArgumentCaptor.forClass(FraudEvaluationAuditDocument.class);
        verify(auditWriter).enqueue(audit.capture());
        assertEquals("11111111-1111-1111-1111-111111111111", audit.getValue().transferId());
        assertEquals("2500", audit.getValue().amountMinor());
        assertSame(FraudDecision.APPROVE, FraudDecision.valueOf(audit.getValue().decision()));
        assertTrue(audit.getValue().latencyMillis() >= 0);
    }
}
