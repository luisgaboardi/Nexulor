# Nexulor

Digital wallet and distributed payment gateway — **showcase portfolio** for senior backend engineering.

Phase 1 delivers a Spring Boot monolith focused on wallet balance and P2P transfers with PostgreSQL ACID guarantees.

## Stack (Phase 1)

- Java 21 (virtual threads enabled)
- Spring Boot 3.4
- Spring Web / Data JPA / Validation / Actuator
- PostgreSQL + Flyway
- Maven
- JUnit 5 + Mockito

## Prerequisites

- JDK 21+
- Maven 3.9+
- Docker (for local PostgreSQL)

## Quick start

```bash
docker compose up -d
./mvnw spring-boot:run
```

On Windows: `.\mvnw.cmd spring-boot:run`

If the wrapper is unavailable, use a local Maven 3.9+ install (`mvn spring-boot:run`).

Health check: `GET http://localhost:8080/actuator/health`

## REST API (Phase 1)

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/wallets` | Open a wallet for an owner |
| `GET` | `/api/v1/wallets/{walletId}` | Fetch wallet by id |
| `GET` | `/api/v1/wallets/by-owner/{ownerId}` | Fetch wallet by owner |
| `POST` | `/api/v1/wallets/{walletId}/credits` | Credit funds (demo/funding helper) |
| `POST` | `/api/v1/transfers` | Execute P2P transfer |
| `GET` | `/api/v1/transfers/{transferId}` | Fetch transfer |
| `GET` | `/api/v1/wallets/{walletId}/transfers` | List transfers for a wallet |

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

# Transfer
curl -s -X POST http://localhost:8080/api/v1/transfers \
  -H "Content-Type: application/json" \
  -d '{"sourceWalletId":"WALLET_A","destinationWalletId":"WALLET_B","amount":25.00,"currency":"BRL"}'
```

## Concurrency model (Phase 1)

Transfers run inside a single database transaction. Both wallets are locked with `SELECT … FOR UPDATE` in **UUID ascending order** to avoid deadlocks. JPA `@Version` remains as a secondary safety net against lost updates.

## Project layout

```
src/main/java/com/nexulor/
  wallet/
    api/              # REST controllers + DTOs
    application/      # use cases + ports
    domain/           # aggregates, value objects, invariants
    infrastructure/   # JPA adapters
docs/
  PRD.md
  ADRs/               # Architecture Decision Records (English)
```

## Roadmap

See [docs/PRD.md](docs/PRD.md). Current scope: **Phase 1 only**.

- Phase 2: Fraud Detection Service + gRPC
- Phase 3: Kafka, Redis locks/idempotency, notifications, Testcontainers
- Phase 4: API Gateway (GraphQL/WebFlux), K8s, Terraform, CI/CD, OpenTelemetry

## Tests

```bash
mvn test
```

Phase 1 tests are unit-level (domain + application). Integration tests with Testcontainers arrive in Phase 3.
