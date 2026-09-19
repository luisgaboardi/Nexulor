package com.nexulor.notification.consumer;

import com.nexulor.notification.readmodel.StatementEntryDocument;
import com.nexulor.notification.readmodel.StatementEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Consumes {@code transaction-completed} and projects two statement entries
 * per event (DEBIT on the source, CREDIT on the destination). Idempotent by
 * construction: the Mongo document id is derived from eventId + walletId, so
 * a redelivered event replaces the same projection instead of duplicating it.
 */
@Component
public class TransactionCompletedConsumer {

    private static final Logger log = LoggerFactory.getLogger(TransactionCompletedConsumer.class);

    private final StatementEntryRepository repository;

    public TransactionCompletedConsumer(StatementEntryRepository repository) {
        this.repository = repository;
    }

    @KafkaListener(topics = "${notification.topics.transaction-completed:transaction-completed}")
    public void onTransactionCompleted(TransactionCompletedMessage message) {
        try {
            repository.save(debitEntry(message));
            repository.save(creditEntry(message));
            log.debug("projected transaction {} into statement read model", message.transferId());
        } catch (RuntimeException e) {
            // Let the error handler retry/seek; a partial projection is repaired
            // by the upsert semantics on redelivery.
            throw e;
        }
    }

    private StatementEntryDocument debitEntry(TransactionCompletedMessage message) {
        return entry(message,
                UUID.fromString(message.sourceWalletId()),
                "DEBIT",
                UUID.fromString(message.destinationWalletId()),
                message.eventId() + ":DEBIT");
    }

    private StatementEntryDocument creditEntry(TransactionCompletedMessage message) {
        return entry(message,
                UUID.fromString(message.destinationWalletId()),
                "CREDIT",
                UUID.fromString(message.sourceWalletId()),
                message.eventId() + ":CREDIT");
    }

    private StatementEntryDocument entry(
            TransactionCompletedMessage message,
            UUID walletId,
            String direction,
            UUID counterpartyWalletId,
            String documentId) {
        StatementEntryDocument doc = new StatementEntryDocument();
        doc.setId(documentId);
        doc.setWalletId(walletId);
        doc.setTransferId(UUID.fromString(message.transferId()));
        doc.setCounterpartyWalletId(counterpartyWalletId);
        doc.setDirection(direction);
        doc.setAmount(message.amount());
        doc.setCurrency(message.currency());
        doc.setCompletedAt(message.completedAt());
        return doc;
    }
}
