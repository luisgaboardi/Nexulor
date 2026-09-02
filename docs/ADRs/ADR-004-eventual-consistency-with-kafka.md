# ADR-004: Handling Eventual Consistency with Kafka

## Status

Proposed (Phase 3)

## Context

Notifications and statement read models should not block the ACID transfer path. Event-driven propagation introduces eventual consistency between the write model and CQRS read models.

## Decision

TBD in Phase 3 — expected direction: publish `transaction-completed` to Kafka after commit; consumers build notifications and statement projections.

## Consequences

TBD.
