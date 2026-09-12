package com.nexulor.fraud.audit;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("fraud_evaluations")
public record FraudEvaluationAuditDocument(
        @Id String id,
        @Indexed String transferId,
        String sourceWalletId,
        String destinationWalletId,
        String amountMinor,
        String currencyCode,
        String decision,
        String ruleId,
        String reason,
        long latencyMillis,
        Instant evaluatedAt) {
}
