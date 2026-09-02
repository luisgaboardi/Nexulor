# Nexulor — Product Vision & Engineering Requirements (PRD/SRD)

**Project:** Distributed Digital Wallet and Payment Gateway  
**System Goal:** Serve as a *Showcase Portfolio* validating Senior Backend Software Engineering competencies for international hiring processes.

## 1. Context and Guidelines for AI Agents

This document is the architectural and functional scope of the project. Any AI agent or developer working on this codebase must adhere to the following engineering principles:

* **Backend Only:** No graphical UI/frontend. Presentation layer is limited to OpenAPI (Swagger) and GraphQL schemas.
* **Senior Code Quality:** Clean Code, SOLID, high cohesion, low coupling.
* **Performance and Concurrency:** Prefer Virtual Threads (Java 21+) for I/O. Race conditions involving financial balances must be handled natively in the design.
* **Failure-Oriented Resilience:** Assume the network fails. Implement Retries, Circuit Breakers, and Timeouts for inter-service communication.
* **Continuous Documentation:** Every complex structural decision must be accompanied by an Architecture Decision Record (ADR).

## 2. Architecture Overview (Distributed Ecosystem)

### 2.1. API Gateway and BFF

* **Scope:** Single entry point for external clients. Routes requests, aggregates data, applies security policies.
* **Stack:** Spring Cloud Gateway, Spring WebFlux.
* **Protocols:** GraphQL (aggregated profile/balance/history queries) and REST.
* **Security:** Spring Security as OAuth2 Resource Server validating JWT tokens.

### 2.2. Wallet & Transaction Service (Financial Core)

* **Scope:** Manages user balances and P2P transfer intent. Requires strong consistency (ACID).
* **Stack:** Java 21, Spring Boot.
* **Database:** PostgreSQL.
* **State & Concurrency:** Redis for distributed locks and temporary balance read cache (Phase 3+).
* **Integration:** REST endpoints for initial transaction submission.

### 2.3. Fraud Detection Service

* **Scope:** Synchronous low-latency risk rules at transaction time.
* **Stack:** Java 21, Spring Boot.
* **Inter-service Communication:** Strictly **gRPC** with the Transaction Service.
* **Storage:** MongoDB (or simulated DynamoDB) for audit logs.

### 2.4. Notification & Statement Service (Event-Driven)

* **Scope:** React to financial domain state changes for user communication and read-model statements.
* **Stack:** Spring Boot, Apache Kafka.
* **Mechanism:** Consume `transaction-completed` topic (published by Wallet Service).
* **Pattern:** CQRS — Statement Service builds optimized read models from events.

## 3. Senior Technical Requirements

* **Transactional Idempotency:** Financial mutation endpoints (e.g. `POST /transfer`) require `Idempotency-Key` header; Redis stores duplicate keys (Phase 3).
* **Integration Tests with Testcontainers:** No in-memory databases (H2) for repository/messaging tests. Provision real PostgreSQL, Redis, and Kafka (Phase 3).
* **ADRs:** Keep `/docs/ADRs` updated in English.

## 4. Infrastructure, Observability, and DevOps (IaC)

* Multi-stage Dockerfiles per microservice.
* Kubernetes manifests or Helm charts for local Minikube/Kind.
* `/terraform` for RDS, MSK, ElastiCache, EKS (AWS simulation).
* GitHub Actions for build, Testcontainers suite, lint/static analysis.
* Observability: Actuator + Micrometer, Prometheus/Grafana via docker-compose, OpenTelemetry distributed tracing.

## 5. Iterative Implementation Roadmap

* **Phase 1 (Domain Foundation) — CURRENT:** Spring Boot monolith for wallet and transfer logic, PostgreSQL, unit tests, basic REST endpoints.
* **Phase 2:** Extract Fraud Detection Service; Dockerize; replace in-process calls with gRPC.
* **Phase 3:** Kafka + Redis; distributed locks; Notification Service; idempotency keys; Testcontainers.
* **Phase 4:** API Gateway with GraphQL/WebFlux; OpenTelemetry; Kubernetes; Terraform; GitHub Actions; ADRs.
