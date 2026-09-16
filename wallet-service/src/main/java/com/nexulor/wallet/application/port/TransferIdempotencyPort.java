package com.nexulor.wallet.application.port;

import java.util.Optional;
import java.util.UUID;

/**
 * Driven port for cross-instance idempotency around transfer submission
 * (PRD section 3). Combines a short-lived distributed lock (mutual exclusion
 * while the first request is in flight) with a stored outcome (replay
 * detection after completion).
 */
public interface TransferIdempotencyPort {

    /**
     * Tries to acquire the idempotency slot for {@code idempotencyKey}.
     *
     * @return ACQUIRED when this call owns the key and may proceed with the
     *         transfer; IN_FLIGHT when another request holds it;
     *         REPLAY_COMPLETED when a previous request finished with the
     *         same fingerprint (caller should replay the stored response);
     *         CONFLICT when a finished request exists with a different
     *         fingerprint.
     */
    Outcome tryBegin(String idempotencyKey, String requestFingerprint);

    /**
     * Stores the final outcome for the key, releasing the distributed lock.
     * The stored entry outlives the lock TTL so late replays are still
     * answered.
     */
    void complete(String idempotencyKey, String requestFingerprint, UUID transferId);

    /**
     * Stored response of a previously completed transfer under this key.
     */
    record StoredResponse(UUID transferId, String requestFingerprint) {
    }

    Optional<StoredResponse> findCompleted(String idempotencyKey);

    enum Outcome {
        ACQUIRED,
        IN_FLIGHT,
        REPLAY_COMPLETED,
        CONFLICT
    }
}
