package com.nexulor.fraud.rules;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-instance counter (Caffeine), default when the service runs without the
 * {@code redis-velocity} profile. Kept from Phase 2 for standalone runs,
 * including the rolling-window expiry semantics; production wiring is the
 * cross-instance {@code RedisVelocityCounter}.
 */
@Component
@Profile("!redis-velocity")
public class LocalVelocityCounter implements VelocityCounterPort {

    private final Map<Long, Cache<String, AtomicInteger>> countersByWindowMillis = new ConcurrentHashMap<>();

    @Override
    public int incrementAndGet(String sourceWalletId, Duration window) {
        Cache<String, AtomicInteger> counters = countersByWindowMillis
                .computeIfAbsent(window.toMillis(), millis ->
                        Caffeine.newBuilder().expireAfterWrite(Duration.ofMillis(millis)).build());
        return counters.get(sourceWalletId, k -> new AtomicInteger()).incrementAndGet();
    }
}
