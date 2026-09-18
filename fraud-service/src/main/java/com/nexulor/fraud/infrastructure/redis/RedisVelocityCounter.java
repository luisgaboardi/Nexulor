package com.nexulor.fraud.infrastructure.redis;

import com.nexulor.fraud.rules.VelocityCounterPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Cross-instance velocity counter backed by Redis (Phase 3, ADR-005 note on
 * R2). INCR + EXPIRE run atomically in Lua so the window restarts cleanly on
 * the first hit: the counter is created with the full window TTL, and each
 * hit inside the window bumps the same key.
 *
 * <p>Fail-open: if Redis is unreachable the rule cannot be evaluated reliably
 * across instances, but fraud evaluation as a whole must still answer the
 * wallet within its deadline — the exception propagates and the engine skips
 * the rule (a single rule failing must not reject every transfer). This
 * trade-off is documented in the ADR and covered by the synchronous rules
 * that run before R2.</p>
 */
@Component
@Profile("redis-velocity")
public class RedisVelocityCounter implements VelocityCounterPort {

    private static final Logger log = LoggerFactory.getLogger(RedisVelocityCounter.class);

    private static final String KEY_PREFIX = "fraud:velocity:";

    /** Atomic INCR with TTL set only when the key is created. */
    private static final DefaultRedisScript<Long> INCREMENT_SCRIPT = new DefaultRedisScript<>("""
            local value = redis.call('INCR', KEYS[1])
            if value == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return value
            """, Long.class);

    private final StringRedisTemplate redis;

    public RedisVelocityCounter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public int incrementAndGet(String sourceWalletId, Duration window) {
        Long value = redis.execute(INCREMENT_SCRIPT,
                List.of(KEY_PREFIX + sourceWalletId),
                Long.toString(window.toMillis()));
        return value == null ? 1 : value.intValue();
    }
}
