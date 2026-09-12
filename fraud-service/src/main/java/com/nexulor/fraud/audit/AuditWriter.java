package com.nexulor.fraud.audit;

/**
 * Port for persisting fraud evaluation audit records. Implemented by the
 * asynchronous Mongo writer; tests can supply in-memory fakes.
 */
public interface AuditWriter {

    void enqueue(FraudEvaluationAuditDocument document);
}
