package com.nexulor.wallet.infrastructure.idempotency;

import com.nexulor.wallet.application.port.TransferIdempotencyPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real Redis (Testcontainers, PRD section 3 — no in-memory substitutes):
 * exercises the Lua scripts under concurrent-style interleavings.
 */
@Testcontainers
class RedisTransferIdempotencyAdapterIT {

    @Container
    @SuppressWarnings("resource")
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private RedisTransferIdempotencyAdapter adapter;

    @BeforeEach
    void setUp() {
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(
                        REDIS.getHost(), REDIS.getMappedPort(6379)));
        connectionFactory.afterPropertiesSet();
        StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
        template.afterPropertiesSet();
        adapter = new RedisTransferIdempotencyAdapter(template, java.time.Duration.ofSeconds(30),
                java.time.Duration.ofHours(24));
    }

    @Test
    void firstRequestAcquiresAndCompletes() {
        String key = "it-" + UUID.randomUUID();

        assertEquals(
                TransferIdempotencyPort.Outcome.ACQUIRED,
                adapter.tryBegin(key, "fp-1"));

        adapter.complete(key, "fp-1", UUID.randomUUID());

        Optional<TransferIdempotencyPort.StoredResponse> stored = adapter.findCompleted(key);
        assertTrue(stored.isPresent());
        assertEquals("fp-1", stored.get().requestFingerprint());
    }

    @Test
    void secondBeginWhileLockedIsInFlight() {
        String key = "it-" + UUID.randomUUID();

        assertEquals(
                TransferIdempotencyPort.Outcome.ACQUIRED,
                adapter.tryBegin(key, "fp-1"));
        assertEquals(
                TransferIdempotencyPort.Outcome.IN_FLIGHT,
                adapter.tryBegin(key, "fp-1"));
    }

    @Test
    void replayAfterCompletionDetectedAndConflictOnDifferentFingerprint() {
        String key = "it-" + UUID.randomUUID();
        UUID transferId = UUID.randomUUID();

        assertEquals(
                TransferIdempotencyPort.Outcome.ACQUIRED,
                adapter.tryBegin(key, "fp-1"));
        adapter.complete(key, "fp-1", transferId);

        assertEquals(
                TransferIdempotencyPort.Outcome.REPLAY_COMPLETED,
                adapter.tryBegin(key, "fp-1"));
        assertEquals(
                TransferIdempotencyPort.Outcome.CONFLICT,
                adapter.tryBegin(key, "fp-2"));

        assertEquals(transferId, adapter.findCompleted(key).orElseThrow().transferId());
    }

    @Test
    void lockTtlReleasesStuckKey() throws InterruptedException {
        String key = "it-" + UUID.randomUUID();
        RedisTransferIdempotencyAdapter shortLock = new RedisTransferIdempotencyAdapter(
                templateForHost(), java.time.Duration.ofMillis(500), java.time.Duration.ofHours(1));

        assertEquals(
                TransferIdempotencyPort.Outcome.ACQUIRED,
                shortLock.tryBegin(key, "fp-1"));
        Thread.sleep(700);
        assertEquals(
                TransferIdempotencyPort.Outcome.ACQUIRED,
                shortLock.tryBegin(key, "fp-1"));
    }

    private StringRedisTemplate templateForHost() {
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        connectionFactory.afterPropertiesSet();
        StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
        template.afterPropertiesSet();
        return template;
    }
}
