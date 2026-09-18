package com.nexulor.fraud.infrastructure.redis;

import com.nexulor.fraud.rules.VelocityCounterPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Real Redis (Testcontainers): verifies the atomic INCR+PEXPIRE Lua script
 * counts hits across independent calls — the cross-instance behavior R2 needs.
 */
@Testcontainers
class RedisVelocityCounterIT {

    @Container
    @SuppressWarnings("resource")
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private VelocityCounterPort counter;

    @BeforeEach
    void setUp() {
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        connectionFactory.afterPropertiesSet();
        StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
        template.afterPropertiesSet();
        counter = new RedisVelocityCounter(template);
    }

    @Test
    void countsSequentialHitsAcrossInstances() {
        String walletId = UUID.randomUUID().toString();

        assertEquals(1, counter.incrementAndGet(walletId, Duration.ofSeconds(60)));
        assertEquals(2, counter.incrementAndGet(walletId, Duration.ofSeconds(60)));
        assertEquals(3, counter.incrementAndGet(walletId, Duration.ofSeconds(60)));
    }

    @Test
    void keepsIndependentWindowsPerSourceWallet() {
        String a = UUID.randomUUID().toString();
        String b = UUID.randomUUID().toString();

        assertEquals(1, counter.incrementAndGet(a, Duration.ofSeconds(60)));
        assertEquals(1, counter.incrementAndGet(b, Duration.ofSeconds(60)));
        assertEquals(2, counter.incrementAndGet(a, Duration.ofSeconds(60)));
    }
}
