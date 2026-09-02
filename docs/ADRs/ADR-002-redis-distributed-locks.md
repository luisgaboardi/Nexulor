# ADR-002: Use of Redis for Distributed Locks

## Status

Proposed (Phase 3)

## Context

When the Wallet service scales horizontally, PostgreSQL row locks alone may be insufficient for cross-instance coordination patterns such as idempotency-key deduplication and short-lived mutual exclusion around transfer initiation.

## Decision

TBD in Phase 3 — expected direction: Redis-based distributed locks plus idempotency-key storage.

## Consequences

TBD.
