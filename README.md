# Nexulor

Digital wallet and distributed payment gateway — **showcase portfolio** for senior backend engineering.

Phase 3 adds **Redis-backed idempotency and distributed locks**, **Kafka event flow** to a new **Notification Service**, **cross-instance fraud velocity**, and a **Testcontainers** integration suite.

## Stack (Phase 3)

- Java 21 (virtual threads enabled)
- Spring Boot 3.4
- Spring Web / Data JPA / Validation / Actuator
- PostgreSQL + Flyway (wallet core)
- MongoDB (fraud audit log + statement read model)
- Redis (idempotency locks, cross-instance velocity counters)
- Apache Kafka (KRaft) — `transaction-completed` topic
- gRPC (contract-first, `fraud/v1/fraud.proto`)
- Resilience4j (timeout, retry, circuit breaker)
- Testcontainers + Failsafe (real Redis/Mongo/Kafka in integration tests)
- Maven (multi-module monorepo)
- JUnit 5 + Mockito + gRPC in-process testing

## Project layout

```
grpc-contracts/        # proto files + generated stubs (contract-first)
wallet-service/        # financial core: wallets, P2P transfers, PostgreSQL
fraud-service/         # fraud rules engine, gRPC server, MongoDB audit
notification-service/  # Kafka consumer, statement read model (CQRS), REST
docs/
  PRD.md
  ADRs/                # Architecture Decision Records (English)
docker-compose.yml     # postgres + mongo + redis + kafka + all services
Dockerfile.*           # multi-stage builds (cached Maven layers, non-root JRE)
```

## Quick start

```bash
docker compose up -d --build
```

This starts PostgreSQL, MongoDB, Redis, Kafka (KRaft), the fraud service (gRPC :9090, health :8081), the wallet service (REST :8080) with the `grpc-fraud` + `redis-velocity` profiles, and the notification service (REST :8082).

Health checks: `GET http://localhost:8080/actuator/health` (wallet), `GET http://localhost:8081/actuator/health` (fraud), `GET http://localhost:8082/actuator/health` (notification).

## REST API (wallet service)

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/wallets` | Open a wallet for an owner |
| `GET` | `/api/v1/wallets/{walletId}` | Fetch wallet by id |
| `GET` | `/api/v1/wallets/by-owner/{ownerId}` | Fetch wallet by owner |
| `POST` | `/api/v1/wallets/{walletId}/credits` | Credit funds (demo/funding helper) |
| `POST` | `/api/v1/transfers` | Execute P2P transfer (**`Idempotency-Key` required**) |
| `GET` | `/api/v1/transfers/{transferId}` | Fetch transfer |
| `GET` | `/api/v1/wallets/{walletId}/transfers` | List transfers for a wallet |

## REST API (notification service)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/wallets/{walletId}/statement` | Statement projection (CQRS read model) |

### Transfer outcomes (`POST /api/v1/transfers`)

| Outcome | HTTP |
|---|---|
| Fraud approves / review (Phase 2) | `201 Created` |
| Fraud rejects | `422 FRAUD_REJECTED` |
| Fraud unreachable after retry/breaker | `503 FRAUD_UNAVAILABLE` (fail-closed, ADR-005) |
| Missing `Idempotency-Key` | `400 MISSING_HEADER` |
| Same key in flight elsewhere | `409 IDEMPOTENCY_KEY_IN_FLIGHT` |
| Same key, different payload | `409 IDEMPOTENCY_KEY_CONFLICT` |

Rules (fraud service): `R3-self-transfer`, `R1-amount-ceiling`, `R2-velocity-source` (cross-instance via Redis, ADR-007). Every evaluation is audited asynchronously to MongoDB.

### Example flow

```bash
# Create wallets
curl -s -X POST http://localhost:8080/api/v1/wallets \
  -H "Content-Type: application/json" \
  -d '{"ownerId":"11111111-1111-1111-1111-111111111111","currency":"BRL"}'

curl -s -X POST http://localhost:8080/api/v1/wallets \
  -H "Content-Type: application/json" \
  -d '{"ownerId":"22222222-2222-2222-2222-222222222222","currency":"BRL"}'

# Credit source (replace WALLET_A)
curl -s -X POST http://localhost:8080/api/v1/wallets/WALLET_A/credits \
  -H "Content-Type: application/json" \
  -d '{"amount":100.00,"currency":"BRL"}'

# Transfer (fraud-checked over gRPC; idempotent via Redis)
curl -s -X POST http://localhost:8080/api/v1/transfers \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: my-order-42" \
  -d '{"sourceWalletId":"WALLET_A","destinationWalletId":"WALLET_B","amount":25.00,"currency":"BRL"}'

# Replay: same key + payload returns the same 201 transfer (no double debit).
# Statement (eventually consistent, built from the Kafka topic):
curl -s http://localhost:8082/api/v1/wallets/WALLET_A/statement
```

## Event flow (Phase 3)

After the transfer commits, the wallet publishes `transaction-completed` to Kafka (ADR-004). The notification service projects two statement entries per event (DEBIT/CREDIT) into MongoDB; projections are idempotent by `eventId + direction`.

## Concurrency model

Transfers run inside a single database transaction. Both wallets are locked with `SELECT … FOR UPDATE` in **UUID ascending order** to avoid deadlocks. JPA `@Version` remains a secondary safety net. Fraud evaluation happens before locking (cheap rejection path). Idempotency is coordinated in Redis (ADR-002): the in-flight lock TTL bounds crash recovery, and completed outcomes are replayed from the 24h record.

## Roadmap

See [docs/PRD.md](docs/PRD.md). Current scope: **Phase 3 (done)**.

- Phase 4: API Gateway (GraphQL/WebFlux), K8s, Terraform, CI/CD, OpenTelemetry

## Tests

```bash
mvnw verify          # unit tests + Testcontainers integration tests (*IT)
mvnw verify -pl wallet-service -am   # single service with dependencies
```

Wallet: domain + application unit tests, Redis idempotency adapter IT (real Redis), Kafka publisher IT (real Kafka). Fraud: rule engine tests, gRPC service tests, in-process gRPC integration test, Redis velocity IT. Notification: consumer E2E IT (Kafka + MongoDB projecting statements).

Requires a running Docker engine for `*IT` (see ADR-008).
