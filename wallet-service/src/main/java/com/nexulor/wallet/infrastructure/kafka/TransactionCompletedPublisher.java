package com.nexulor.wallet.infrastructure.kafka;

import com.nexulor.wallet.application.TransactionCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Kafka adapter that publishes {@code transaction-completed} (ADR-004) after
 * the transfer's ACID transaction commits. The application layer publishes a
 * Spring application event; this listener fires only in the AFTER_COMMIT
 * phase, so a rollback never emits a phantom completion. Kafka send failures
 * are logged, never thrown into the caller — the financial outcome is already
 * durable and the notification path is eventually consistent by design.
 */
@Component
public class TransactionCompletedPublisher {

    private static final Logger log = LoggerFactory.getLogger(TransactionCompletedPublisher.class);

    private final KafkaTemplate<String, TransactionCompletedEvent> kafkaTemplate;
    private final String topic;

    public TransactionCompletedPublisher(
            KafkaTemplate<String, TransactionCompletedEvent> kafkaTemplate,
            @Value("${wallet.events.transaction-completed-topic:transaction-completed}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTransferCompleted(TransactionCompletedEvent event) {
        kafkaTemplate.send(topic, event.transferId().toString(), event)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        log.error("failed to publish transaction-completed for transfer {}: {}",
                                event.transferId(), error.toString());
                    } else {
                        log.debug("published transaction-completed for transfer {} to {}[{}]",
                                event.transferId(), topic, result.getRecordMetadata().offset());
                    }
                });
    }
}
