package com.nexulor.fraud.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Asynchronous audit writer: evaluations are enqueued on the gRPC response
 * path and persisted by a background thread, so audit I/O never adds
 * latency to the latency-sensitive evaluation call. Records are logged
 * and dropped (bounded queue) if Mongo is unavailable — audit is
 * best-effort and must never block or fail transfers.
 */
@Component
public class AsyncAuditWriter implements AuditWriter {

    private static final Logger log = LoggerFactory.getLogger(AsyncAuditWriter.class);
    private static final int QUEUE_CAPACITY = 10_000;

    private final BlockingQueue<FraudEvaluationAuditDocument> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Thread worker;

    public AsyncAuditWriter(MongoTemplate mongoTemplate) {
        this.worker = Thread.ofPlatform()
                .name("fraud-audit-writer")
                .daemon(true)
                .start(() -> drain(mongoTemplate));
    }

    @Override
    public void enqueue(FraudEvaluationAuditDocument document) {
        if (!queue.offer(document)) {
            log.warn("fraud audit queue full; dropping evaluation record for transfer {}", document.transferId());
        }
    }

    private void drain(MongoTemplate mongoTemplate) {
        while (running.get()) {
            try {
                FraudEvaluationAuditDocument document = queue.take();
                document = new FraudEvaluationAuditDocument(
                        document.id(),
                        document.transferId(),
                        document.sourceWalletId(),
                        document.destinationWalletId(),
                        document.amountMinor(),
                        document.currencyCode(),
                        document.decision(),
                        document.ruleId(),
                        document.reason(),
                        document.latencyMillis(),
                        Instant.now());
                mongoTemplate.insert(document);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.error("failed to persist fraud audit record", e);
            }
        }
    }
}
