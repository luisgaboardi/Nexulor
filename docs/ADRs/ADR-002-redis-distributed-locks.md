# ADR-002: Use of Redis for Distributed Locks

## Status

Accepted (Phase 3)

## Context

When the Wallet service scales horizontally, PostgreSQL row locks alone are insufficient for cross-instance coordination around transfer submission. The idempotency protocol (PRD section 3) needs two primitives PostgreSQL cannot provide cheaply on the hot path: short-lived mutual exclusion across instances while the first request is in flight, and a TTL-bounded record of prior outcomes for replays.

## Decision

* Implement transfer idempotency on Redis via a driven port (`TransferIdempotencyPort`) in the application layer, with a `StringRedisTemplate` adapter in infrastructure.
* One Redis hash per key (`wallet:idem:{key}`) holds the request fingerprint (SHA-256 of the canonical transfer payload), the completed `transferId`, and a `lock` marker.
* `tryBegin` runs atomically in Lua: a finished entry is answered from cache (replay) or rejected (fingerprint conflict); otherwise the lock is acquired with `SET NX PX`, so a crashed holder self-releases after the lock TTL (30s default).
* `complete` stores the outcome and drops the lock in one atomic step; the record then persists for 24h (configurable) to answer late replays.
* Request validation (self-transfer, zero amount) runs **before** the idempotency protocol so invalid requests never consume a slot.
* PostgreSQL pessimistic locks (ADR-001) remain the balance consistency mechanism; Redis never holds financial state, only idempotency coordination.

## Consequences

* Replays of the same key + payload return the original 201 response; the same key with a different payload is a 409 (`IDEMPOTENCY_KEY_CONFLICT`), signalling a client bug instead of silently duplicating or diverging.
* Concurrent duplicate submissions are serialized: the loser gets 409 `IDEMPOTENCY_KEY_IN_FLIGHT` and may retry.
* Wallet now requires a reachable Redis on the transfer path; a Redis outage fails transfers closed rather than risking duplicate financial mutations.
* TTLs bound operational risk: no manual cleanup, no unbounded key growth, no permanently wedged keys.
