# Nexulor

Digital wallet and distributed payment gateway — **showcase portfolio** for senior backend engineering.

Phase 3 adds **Redis-backed idempotency and distributed locks**, **Kafka event flow** to a new **Notification Service**, **cross-instance fraud velocity**, and a **Testcontainers** integration suite. Phase 4 (in progress) adds an **API Gateway** — Spring Cloud Gateway REST routing plus a GraphQL aggregation endpoint on WebFlux (ADR-009).

## Stack

- Java 21 (virtual threads enabled)
- Spring Boot 3.4
- Spring Cloud Gateway + Spring GraphQL / WebFlux (API Gateway, Phase 4)
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
api-gateway/           # single entry point: REST routing + GraphQL aggregation (Phase 4)
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

This starts PostgreSQL, MongoDB, Redis, Kafka (KRaft), the fraud service (gRPC :9090, health :8081), the wallet service (REST :8080) with the `grpc-fraud` + `redis-velocity` profiles, the notification service (REST :8082), and the API Gateway (REST routing + GraphQL on :8088).

Health checks: `GET http://localhost:8088/actuator/health` (gateway), `GET http://localhost:8080/actuator/health` (wallet), `GET http://localhost:8081/actuator/health` (fraud), `GET http://localhost:8082/actuator/health` (notification).

## API Gateway (Phase 4, ADR-009)

Single entry point for external clients on **`:8088`**:

- **REST routing** — every wallet/transfer/statement path below also works through the gateway (`http://localhost:8088/api/v1/...`); requests are proxied to the owning service.
- **GraphQL** — `POST /graphql` (GraphiQL UI at `/graphiql`):

```graphql
query {
  walletSummary(ownerId: "11111111-1111-1111-1111-111111111111") {
    wallet { balance currency }
    recentTransfers { amount status }
    statement { direction amount }
    partialErrors
  }
}
```

`walletSummary` fans out to the wallet core and the statement read model in parallel and stitches one response. If the statement service is down the query still returns, with the failure reported in `partialErrors` (graceful degradation — the statement is an eventual read model).

### Security (Phase 4, ADR-010)

The gateway is an **OAuth2 Resource Server** validating JWTs (PRD 2.1):

| Request | Token |
|---|---|
| `POST /api/v1/wallets`, `POST /api/v1/wallets/*/credits`, `POST /api/v1/transfers` | **required** (`401` without/invalid) |
| GET routes, `POST /graphql` (queries), GraphiQL, actuator | public |

Local runs use the `jwt-local` profile (HS256 shared secret, demo only). Production swaps the decoder via `spring.security.oauth2.resourceserver.jwt.issuer-uri` (JWKS/RS256) with zero changes to the security rules.

Mint a local demo token (requires `openssl`):

```bash
SECRET="local-demo-secret-change-me-0123456789abcdef"
b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }
H=$(printf '{"alg":"HS256","typ":"JWT"}' | b64url)
P=$(printf '{"sub":"demo-user","iss":"local","exp":%s,"iat":%s}' "$(($(date +%s)+300))" "$(date +%s)" | b64url)
SIG=$(printf '%s.%s' "$H" "$P" | openssl dgst -sha256 -hmac "$SECRET" -binary | b64url)
TOKEN="$H.$P.$SIG"

curl -s -X POST http://localhost:8088/api/v1/transfers \
  -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: my-order-42" \
  -H "Content-Type: application/json" \
  -d '{"sourceWalletId":"WALLET_A","destinationWalletId":"WALLET_B","amount":25.00,"currency":"BRL"}'
```

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

## Observability: distributed tracing (Phase 4, ADR-011)

Every service reports spans to **Zipkin** via Micrometer Tracing (Brave bridge), with W3C `traceparent` propagation across HTTP, gRPC and Kafka. One request = one trace, including the async leg:

```
gateway (auth + route) -> wallet (tx) -> fraud (gRPC) -> [kafka] -> notification (projection)
```

- **Zipkin UI:** http://localhost:9411 — search by service (e.g. `nexolor-gateway`) or tag `http.route=/api/v1/transfers`, then click a trace to see the full timing tree.
- **Sampling:** 100% for the showcase (`management.tracing.sampling.probability: 1.0`); tune per environment via env.
- **Endpoint:** `ZIPKIN_ENDPOINT` env per service (compose sets `http://zipkin:9411/api/v2/spans`).
- The full stack is 9 containers: Postgres, Mongo, Redis, Kafka, wallet, fraud, notification, gateway, Zipkin.

See [ADR-011](docs/ADRs/ADR-011-distributed-tracing-micrometer-brave.md) for the design, including the gRPC virtual-thread rescope and Kafka observation toggles.

## Kubernetes (Phase 4)

Kustomize manifests in [`k8s/`](k8s/) deploy the whole stack — 4 services + Zipkin + Postgres/Mongo/Redis/Kafka — into the `nexolor` namespace, with the tracing config carried over (`ZIPKIN_ENDPOINT` points at the in-cluster Zipkin, so the same end-to-end trace works):

- `k8s/base` — namespace, shared `ConfigMap`/`Secret`, StatefulSets (postgres, mongo), Deployments (redis, kafka, zipkin), all 4 services with readiness/liveness probes on `/actuator/health` (fraud: gRPC 9090 + health 8081), Services and a NodePort `30088` entrypoint for the gateway.
- `k8s/overlays/local` — adds the `jwt-local` profile for local clusters (Docker Desktop, kind, minikube).

```bash
# 1. build + load images (tags must match k8s/base: nexolor/<service>:latest)
./mvnw -pl wallet-service -am spring-boot:build-image -DskipTests   # repeat per service
kind load docker-image nexolor/wallet-service:latest                  # or minikube image load / Docker Desktop resolves locally

# 2. deploy
kubectl apply -k k8s/overlays/local

# 3. use it
kubectl -n nexolor port-forward svc/api-gateway 8088:8088
kubectl -n nexolor port-forward svc/zipkin 9411:9411
```

Secrets are inline demo values — production would swap in a secrets manager, PVs and replicated data stores (noted in the manifests).

## Terraform (Phase 4)

[`terraform/`](terraform/) provisions the cloud topology on AWS — VPC, EKS, RDS PostgreSQL 16 (wallet store), MSK Kafka 3.6 (transaction-completed) and ElastiCache Redis 7 (idempotency/velocity) — with remote state in S3 + DynamoDB locking. See the [Terraform README](terraform/README.md) for the one-time state bootstrap and per-environment usage.

```bash
cd terraform
terraform init -backend-config="bucket=nexolor-tfstate-<account-id>" ...
terraform plan -var-file=envs/dev.tfvars
terraform apply -var-file=envs/dev.tfvars
```

Outputs feed the k8s ConfigMap (`SPRING_DATASOURCE_URL`, `SPRING_KAFKA_BOOTSTRAP_SERVERS`, `SPRING_DATA_REDIS_HOST`); RDS credentials go to Secrets Manager, not to state-only variables. Mongo and Zipkin are the next increment (DocumentDB / self-hosted on EKS).

## Roadmap

See [docs/PRD.md](docs/PRD.md). Current scope: **Phase 4 (in progress)** — API Gateway, OAuth2/JWT, CI/CD, distributed tracing, Kubernetes manifests and Terraform done; remaining: Mongo/Zipkin as managed services (DocumentDB / self-hosted).

- Phase 4 remaining: DocumentDB (mongo) + Zipkin on EKS

## Tests

```bash
mvnw verify          # unit tests + Testcontainers integration tests (*IT)
mvnw verify -pl wallet-service -am   # single service with dependencies
```

Wallet: domain + application unit tests, Redis idempotency adapter IT (real Redis), Kafka publisher IT (real Kafka). Fraud: rule engine tests, gRPC service tests, in-process gRPC integration test, Redis velocity IT. Notification: consumer E2E IT (Kafka + MongoDB projecting statements).

Requires a running Docker engine for `*IT` (see ADR-008).

## CI

GitHub Actions (`.github/workflows/ci.yml`) runs `mvnw verify` on every push/PR to `master` on `ubuntu-latest`: unit tests + Testcontainers ITs against the runner's Docker engine, with Maven repository caching. Test failure artifacts (surefire/failsafe reports) are uploaded for 7 days.
