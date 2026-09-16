package com.nexulor.wallet.infrastructure.idempotency;

import com.nexulor.wallet.application.port.TransferIdempotencyPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Redis-backed implementation of the idempotency port (ADR-002).
 *
 * <p>State per key, stored as a Redis hash {@code wallet:idem:{key}}:</p>
 * <ul>
 *   <li>{@code fingerprint} — SHA-256 of the transfer request body, so a
 *       replayed key with a different payload is a client bug (409), not a
 *       silent duplicate;</li>
 *   <li>{@code transferId} — set only on completion, marking the request as
 *       finished and answerable from cache;</li>
 *   <li>{@code lock} — set to "1" only while the first request is in flight.</li>
 * </ul>
 *
 * <p>Concurrency protocol:</p>
 * <ol>
 *   <li>{@code tryBegin} atomically (Lua) checks existing state, then
 *       acquires the lock with SET NX PX when no finished entry exists.</li>
 *   <li>The in-flight lock expires after {@code lock-ttl} so a crashed
 *       instance cannot wedge the key forever.</li>
 *   <li>{@code complete} atomically (Lua) stores the outcome and removes the
 *       lock; the entry then persists for {@code record-ttl} for replays.</li>
 * </ol>
 */
@Component
public class RedisTransferIdempotencyAdapter implements TransferIdempotencyPort {

    private static final Logger log = LoggerFactory.getLogger(RedisTransferIdempotencyAdapter.class);

    static final String KEY_PREFIX = "wallet:idem:";

    private static final String FIELD_FINGERPRINT = "fingerprint";
    private static final String FIELD_TRANSFER_ID = "transferId";
    private static final String FIELD_LOCK = "lock";

    /** Atomic tryBegin: reject on finished entries, SET NX PX otherwise. */
    private static final DefaultRedisScript<String> TRY_BEGIN_SCRIPT = new DefaultRedisScript<>("""
            local existing = redis.call('HGETALL', KEYS[1])
            local fingerprint = nil
            local transferId = nil
            for i = 1, #existing, 2 do
              if existing[i] == 'fingerprint' then fingerprint = existing[i + 1] end
              if existing[i] == 'transferId' then transferId = existing[i + 1] end
            end
            if transferId then
              if fingerprint == ARGV[1] then return 'REPLAY_COMPLETED' else return 'CONFLICT' end
            end
            if redis.call('SET', KEYS[1] .. ':lock', '1', 'NX', 'PX', ARGV[2]) then
              if not fingerprint then
                redis.call('HSET', KEYS[1], 'fingerprint', ARGV[1])
                redis.call('EXPIRE', KEYS[1], ARGV[3])
              end
              return 'ACQUIRED'
            end
            return 'IN_FLIGHT'
            """, String.class);

    /** Atomic complete: store outcome, drop the lock. */
    private static final DefaultRedisScript<String> COMPLETE_SCRIPT = new DefaultRedisScript<>("""
            redis.call('HSET', KEYS[1], 'transferId', ARGV[1])
            redis.call('EXPIRE', KEYS[1], ARGV[2])
            redis.call('DEL', KEYS[1] .. ':lock')
            return 'OK'
            """, String.class);

    private final StringRedisTemplate redis;
    private final Duration lockTtl;
    private final Duration recordTtl;

    public RedisTransferIdempotencyAdapter(
            StringRedisTemplate redis,
            @Value("${wallet.idempotency.lock-ttl:PT30S}") Duration lockTtl,
            @Value("${wallet.idempotency.record-ttl:P24H}") Duration recordTtl) {
        this.redis = redis;
        this.lockTtl = lockTtl;
        this.recordTtl = recordTtl;
    }

    @Override
    public Outcome tryBegin(String idempotencyKey, String requestFingerprint) {
        String key = KEY_PREFIX + idempotencyKey;
        String result = redis.execute(TRY_BEGIN_SCRIPT,
                List.of(key),
                requestFingerprint,
                Long.toString(lockTtl.toMillis()),
                Long.toString(recordTtl.toSeconds()));
        return Outcome.valueOf(result);
    }

    @Override
    public void complete(String idempotencyKey, String requestFingerprint, UUID transferId) {
        String key = KEY_PREFIX + idempotencyKey;
        redis.execute(COMPLETE_SCRIPT,
                List.of(key),
                transferId.toString(),
                Long.toString(recordTtl.toSeconds()));
        log.debug("idempotency key {} completed with transfer {}", idempotencyKey, transferId);
    }

    @Override
    public Optional<StoredResponse> findCompleted(String idempotencyKey) {
        String key = KEY_PREFIX + idempotencyKey;
        Object transferId = redis.opsForHash().get(key, FIELD_TRANSFER_ID);
        if (transferId == null) {
            return Optional.empty();
        }
        Object fingerprint = redis.opsForHash().get(key, FIELD_FINGERPRINT);
        return Optional.of(new StoredResponse(
                UUID.fromString(transferId.toString()),
                fingerprint == null ? null : fingerprint.toString()));
    }
}
