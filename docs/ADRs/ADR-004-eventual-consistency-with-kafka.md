# ADR-004: Handling Eventual Consistency with Kafka

## Status

Accepted (Phase 3)

## Context

Notifications and statement read models must not block the ACID transfer path. Event-driven propagation introduces eventual consistency between the write model and CQRS read models, and the transport choice determines ordering, redelivery, and coupling properties.

## Decision

* The wallet publishes `transaction-completed` to Kafka **after commit** only. The application layer publishes a Spring application event; an `@TransactionalEventListener(AFTER_COMMIT)` adapter sends it via `KafkaTemplate`, so a rollback never emits a phantom completion.
* Message key is the `transferId` (ordering per transfer); the payload is a versioned envelope (`eventType`, `eventVersion=1`) serialized as JSON **without type headers** — consumers are tolerant readers that map the envelope to their own payload class.
* Kafka send failures are logged, never propagated to the caller: the financial outcome is already durable and the notification path is eventually consistent by design (at-least-once delivery).
* The notification-service consumes the topic and projects two statement entries per event (DEBIT on source, CREDIT on destination) into MongoDB. Projections are idempotent: the Mongo document id is `eventId + ":" + direction`, so redelivery upserts instead of duplicating.
* The statement read model is a pure projection and can be rebuilt from the topic at any time (compaction-friendly envelope).

## Consequences

* Statement/notifications lag the write model by the Kafka round-trip (typically milliseconds); the API contract of the statement endpoint is read-only and eventually consistent by definition.
* At-least-once means consumers must be idempotent (they are, by document id).
* Schema evolution is additive: new fields are tolerated by consumers; `eventVersion` gates breaking changes.
