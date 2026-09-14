# Nexulor

Digital wallet and distributed payment gateway — **showcase portfolio** for senior backend engineering.

Phase 2 extracts the **Fraud Detection Service** and connects it to the wallet core over **gRPC**, with both services containerized via multi-stage Docker builds.

## Stack (Phase 2)

- Java 21 (virtual threads enabled)
- Spring Boot 3.4
- Spring Web / Data JPA / Validation / Actuator
- PostgreSQL + Flyway (wallet core)
- MongoDB (fraud audit log)
- gRPC (contract-first, `fraud/v1/fraud.proto`)
- Resilience4j (timeout, retry, circuit breaker)
- Maven (multi-module monorepo)
- JUnit 5 + Mockito + gRPC in-process testing

## Project layout

```
grpc-contracts/        # proto files + generated stubs (contract-first)
wallet-service/        # financial core: wallets, P2P transfers, PostgreSQL
fraud-service/         # fraud rules engine, gRPC server, MongoDB audit
docs/
  PRD.md
  PHASE-2-PLAN.md
  ADRs/                # Architecture Decision Records (English)
docker-compose.yml     # postgres + mongo + both services
Dockerfile.*           # multi-stage builds (cached Maven layers, non-root JRE)
```

## Quick start

```bash
docker compose up -d --build
```

This starts PostgreSQL, MongoDB, the fraud service (gRPC :9090, health :8081) and the wallet service (REST :8080) with the `grpc-fraud` profile active.

Standalone (without Docker): `docker compose up -d postgres mongo`, then `mvnw spring-boot:run` in each service directory — the wallet defaults to a loud **noop fraud adapter** (approves everything; see ADR-005) unless run with `--spring.profiles.active=grpc-fraud` and a reachable fraud service.

Health checks: `GET http://localhost:8080/actuator/health` (wallet) and `GET http://localhost:8081/actuator/health` (fraud).

## REST API (wallet service)

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/wallets` | Open a wallet for an owner |
| `GET` | `/api/v1/wallets/{walletId}` | Fetch wallet by id |
| `GET` | `/api/v1/wallets/by-owner/{ownerId}` | Fetch wallet by owner |
| `POST` | `/api/v1/wallets/{walletId}/credits` | Credit funds (demo/funding helper) |
| `POST` | `/api/v1/transfers` | Execute P2P transfer (fraud-checked) |
| `GET` | `/api/v1/transfers/{transferId}` | Fetch transfer |
| `GET` | `/api/v1/wallets/{walletId}/transfers` | List transfers for a wallet |

### Fraud integration (Phase 2)

`POST /api/v1/transfers` calls the Fraud Detection Service **before** any lock or balance mutation:

| Outcome | HTTP |
|---|---|
| Fraud approves / review (Phase 2) | `201 Created` |
| Fraud rejects | `422 FRAUD_REJECTED` |
| Fraud unreachable after retry/breaker | `503 FRAUD_UNAVAILABLE` (fail-closed, ADR-005) |

Rules (fraud service): `R3-self-transfer`, `R1-amount-ceiling`, `R2-velocity-source` — configurable via `fraud.rules.*`. Every evaluation is audited asynchronously to MongoDB.

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

# Transfer (evaluated by the fraud service over gRPC)
curl -s -X POST http://localhost:8080/api/v1/transfers \
  -H "Content-Type: application/json" \
  -d '{"sourceWalletId":"WALLET_A","destinationWalletId":"WALLET_B","amount":25.00,"currency":"BRL"}'
```

## Concurrency model

Transfers run inside a single database transaction. Both wallets are locked with `SELECT … FOR UPDATE` in **UUID ascending order** to avoid deadlocks. JPA `@Version` remains as a secondary safety net against lost updates. Fraud evaluation happens before locking (cheap rejection path).

## Roadmap

See [docs/PRD.md](docs/PRD.md). Current scope: **Phase 2**.

- Phase 3: Kafka, Redis locks/idempotency, notifications, Testcontainers
- Phase 4: API Gateway (GraphQL/WebFlux), K8s, Terraform, CI/CD, OpenTelemetry

## Tests

```bash
mvnw test            # all modules
mvnw test -pl fraud-service -am   # single service with dependencies
```

Wallet: domain + application unit tests. Fraud: rule engine tests, gRPC service tests, and an **in-process gRPC integration test** (real serialization + status codes, no network).
