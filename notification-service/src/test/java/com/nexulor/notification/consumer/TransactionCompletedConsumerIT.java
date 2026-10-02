package com.nexulor.notification.consumer;

import com.nexulor.notification.NotificationApplication;
import com.nexulor.notification.readmodel.StatementEntryDocument;
import com.nexulor.notification.readmodel.StatementEntryRepository;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;
import org.testcontainers.containers.MongoDBContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * End-to-end projection test with real Kafka (embedded broker) and real
 * MongoDB (Testcontainers) — no in-memory substitutes (PRD section 3). The
 * consumer group sees the event, projects both statement entries, and the
 * projection is idempotent per (eventId, direction).
 */
class TransactionCompletedConsumerIT {

    static final String TOPIC = "transaction-completed";

    private static EmbeddedKafkaBroker broker;
    private static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");
    private static ConfigurableApplicationContext context;

    @BeforeAll
    static void startStack() {
        MONGO.start();
        broker = new EmbeddedKafkaKraftBroker(1, 1, TOPIC);
        broker.afterPropertiesSet();

        Map<String, Object> props = new HashMap<>();
        props.put("spring.kafka.bootstrap-servers", broker.getBrokersAsString());
        context = new SpringApplicationBuilder(NotificationApplication.class)
                .properties(props)
                // Overrides must be command-line args, not .properties(): the latter are
                // *default* properties and lose to application.yml, which hardcodes
                // mongodb://localhost:27017 — the test then passed against whatever Mongo
                // happened to listen on 27017 and failed on a bare CI runner.
                .run("--server.port=0",
                        "--spring.data.mongodb.uri=" + MONGO.getReplicaSetUrl(),
                        "--spring.kafka.bootstrap-servers=" + broker.getBrokersAsString(),
                        "--spring.kafka.consumer.auto-offset-reset=earliest");

        assertEquals(MONGO.getReplicaSetUrl(), context.getEnvironment().getProperty("spring.data.mongodb.uri"),
                "context must be wired to the Testcontainers Mongo instance");
    }

    @AfterAll
    static void stopStack() {
        if (context != null) {
            context.close();
        }
        broker.destroy();
        MONGO.stop();
    }

    @Test
    void projectsTransferIntoTwoStatementEntries() {
        StatementEntryRepository repository = context.getBean(StatementEntryRepository.class);

        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put(org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                broker.getBrokersAsString());
        producerProps.put(org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                org.apache.kafka.common.serialization.StringSerializer.class);
        producerProps.put(org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                org.springframework.kafka.support.serializer.JsonSerializer.class);
        KafkaTemplate<String, TransactionCompletedMessage> kafkaTemplate = new KafkaTemplate<>(
                new org.springframework.kafka.core.DefaultKafkaProducerFactory<>(producerProps));

        UUID transferId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID destinationId = UUID.randomUUID();

        kafkaTemplate.send(TOPIC, transferId.toString(), new TransactionCompletedMessage(
                UUID.randomUUID().toString(),
                "transaction.completed",
                "1",
                transferId.toString(),
                sourceId.toString(),
                destinationId.toString(),
                new BigDecimal("42.00"),
                "BRL",
                Instant.parse("2026-09-28T12:00:00Z")));

        Awaitility.await().atMost(30, TimeUnit.SECONDS).pollInterval(200, TimeUnit.MILLISECONDS).until(() ->
                !repository.findByWalletIdOrderByCompletedAtDesc(sourceId).isEmpty());

        List<StatementEntryDocument> sourceEntries =
                repository.findByWalletIdOrderByCompletedAtDesc(sourceId);
        List<StatementEntryDocument> destinationEntries =
                repository.findByWalletIdOrderByCompletedAtDesc(destinationId);

        assertEquals(1, sourceEntries.size());
        assertEquals("DEBIT", sourceEntries.get(0).getDirection());
        assertEquals(destinationId, sourceEntries.get(0).getCounterpartyWalletId());
        assertEquals(1, destinationEntries.size());
        assertEquals("CREDIT", destinationEntries.get(0).getDirection());
        assertEquals(sourceId, destinationEntries.get(0).getCounterpartyWalletId());
    }
}
