# ADR-008: Testcontainers over In-Memory Substitutes for Integration Tests

## Status

Accepted (Phase 3)

## Context

The PRD (section 3) mandates Testcontainers with real PostgreSQL, Redis, and Kafka for repository/messaging integration tests, banning in-memory databases (H2) for repository/messaging tests. The build needed a repeatable way to run these tests on developer machines (Windows + Docker Desktop) and in CI.

## Decision

* Integration tests are named `*IT` and run by the Maven Failsafe plugin during `mvn verify`; unit tests stay on Surefire (`*Test`), keeping the fast inner loop unaffected.
* Real dependencies per test: Redis (generic container) for the idempotency adapter and velocity counter; MongoDB for the notification projection; embedded Kafka broker (spring-kafka-test, KRaft) for topic publish/consume paths.
* Docker access is pinned via `DOCKER_HOST` in the Failsafe configuration so Testcontainers reaches the Docker Desktop engine pipe (`npipe:////./pipe/docker_engine`) deterministically; CI can override it per environment.
* No Spring context where one is not needed: adapter tests wire plain objects against container-backed clients; the notification E2E test boots the real application against embedded Kafka + Mongo container.

## Consequences

* `mvn verify` requires a running Docker engine; without one, unit tests still pass but `*IT` fail fast with a clear Testcontainers error.
* Tests exercise the actual serialization/locking/TTL semantics of the real systems — several real bugs (Kafka type-header coupling, Redis Lua edge cases) were caught here and would have been invisible with fakes.
* Slightly longer builds (container startup, seconds per test class), mitigated by Testcontainers reuse patterns if needed later.
