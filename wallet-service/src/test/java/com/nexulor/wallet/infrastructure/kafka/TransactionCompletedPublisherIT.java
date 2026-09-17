package com.nexulor.wallet.infrastructure.kafka;

import com.nexulor.wallet.application.TransactionCompletedEvent;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Real Kafka (embedded broker, PRD section 3 — no in-memory substitutes):
 * verifies the publisher writes a deserializable event to
 * {@code transaction-completed} keyed by transfer id. The AFTER_COMMIT phasing
 * is enforced by the service layer, which requires an active transaction.
 */
class TransactionCompletedPublisherIT {

    static final String TOPIC = "transaction-completed";

    private static EmbeddedKafkaBroker broker;

    @BeforeAll
    static void startBroker() {
        broker = new EmbeddedKafkaKraftBroker(1, 1, TOPIC);
        broker.afterPropertiesSet();
    }

    @AfterAll
    static void stopBroker() {
        broker.destroy();
    }

    @Test
    void publishesDeserializableEvent() {
        Map<String, Object> producerProps = KafkaTestUtils.producerProps(broker);
        producerProps.put(org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                org.apache.kafka.common.serialization.StringSerializer.class);
        producerProps.put(org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                org.springframework.kafka.support.serializer.JsonSerializer.class);
        KafkaTemplate<String, TransactionCompletedEvent> template = new KafkaTemplate<>(
                new DefaultKafkaProducerFactory<>(producerProps));

        TransactionCompletedPublisher publisher =
                new TransactionCompletedPublisher(template, TOPIC);
        UUID transferId = UUID.randomUUID();
        publisher.onTransferCompleted(TransactionCompletedEvent.from(
                transferId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("15.50"),
                "BRL",
                Instant.parse("2026-09-28T12:00:00Z")));

        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("it-group", "true", broker);
        consumerProps.put(org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                org.apache.kafka.common.serialization.StringDeserializer.class);
        consumerProps.put(org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                org.springframework.kafka.support.serializer.JsonDeserializer.class);
        consumerProps.put(org.springframework.kafka.support.serializer.JsonDeserializer.TRUSTED_PACKAGES, "*");
        consumerProps.put(org.springframework.kafka.support.serializer.JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        consumerProps.put(org.springframework.kafka.support.serializer.JsonDeserializer.VALUE_DEFAULT_TYPE,
                TransactionCompletedEvent.class);
        try (Consumer<String, TransactionCompletedEvent> consumer =
                new DefaultKafkaConsumerFactory<String, TransactionCompletedEvent>(consumerProps)
                        .createConsumer()) {
            broker.consumeFromAnEmbeddedTopic(consumer, TOPIC);
            ConsumerRecord<String, TransactionCompletedEvent> record =
                    KafkaTestUtils.getSingleRecord(consumer, TOPIC);
            assertEquals(transferId.toString(), record.key());
            assertEquals("transaction.completed", record.value().eventType());
        }
    }
}
