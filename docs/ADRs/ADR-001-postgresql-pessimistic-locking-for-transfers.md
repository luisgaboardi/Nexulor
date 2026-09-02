# ADR-001: PostgreSQL Pessimistic Locking for P2P Transfers

## Status

Accepted (Phase 1)

## Context

Concurrent P2P transfers against the same wallet can produce lost updates or negative balances if balance mutations are not serialized. Phase 1 is a single-node Spring Boot service backed by PostgreSQL; Redis distributed locks are deferred to Phase 3.

## Decision

* Execute each transfer inside one ACID database transaction.
* Acquire `PESSIMISTIC_WRITE` locks (`SELECT … FOR UPDATE`) on both wallets.
* Lock wallets in ascending UUID order to prevent deadlocks when two transfers touch the same pair in opposite directions.
* Keep JPA `@Version` as a secondary guard against lost updates outside the locked transfer path.

## Consequences

* Strong consistency for wallet balances within Phase 1.
* Throughput for a hot wallet is limited by row-level lock contention (acceptable for the monolith stage).
* Phase 3 may introduce Redis distributed locks and idempotency keys for multi-instance deployments; this ADR remains valid for the database-backed critical section unless superseded.
