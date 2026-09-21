# Architecture Decision Records

ADRs document significant architectural choices for Nexulor.

All ADRs are written in **English**.

## Index

| ADR | Title | Status | Phase |
|-----|-------|--------|-------|
| [ADR-000](ADR-000-record-architecture-decisions.md) | Record architecture decisions | Accepted | 1 |
| [ADR-001](ADR-001-postgresql-pessimistic-locking-for-transfers.md) | PostgreSQL pessimistic locking for P2P transfers | Accepted | 1 |
| [ADR-002](ADR-002-redis-distributed-locks.md) | Redis for distributed locks and transfer idempotency | Accepted | 3 |
| [ADR-003](ADR-003-grpc-vs-rest-for-fraud.md) | gRPC vs REST for Fraud Service Intercom | Accepted | 2 |
| [ADR-004](ADR-004-eventual-consistency-with-kafka.md) | Handling Eventual Consistency with Kafka | Accepted | 3 |
| [ADR-005](ADR-005-fail-closed-on-fraud-unavailability.md) | Fail-closed on fraud unavailability; REVIEW semantics | Accepted | 2 |
| [ADR-006](ADR-006-multimodule-monorepo-with-contract-first-proto.md) | Multi-module monorepo with contract-first proto | Accepted | 2 |
| [ADR-007](ADR-007-cross-instance-velocity-redis.md) | Cross-instance velocity counting with Redis (fail-open) | Accepted | 3 |
| [ADR-008](ADR-008-testcontainers-integration-tests.md) | Testcontainers over in-memory substitutes | Accepted | 3 |
