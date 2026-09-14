# ADR-003: gRPC vs REST for Fraud Service Intercom

## Status

Accepted (Phase 2)

## Context

Fraud checks are synchronous and latency-sensitive on the transfer path. Protocol choice between gRPC and REST affects serialization overhead, contract evolution, and operational tooling. The PRD mandates gRPC between the Wallet/Transaction Service and the Fraud Detection Service.

## Decision

Adopt **contract-first gRPC** (`fraud/v1/fraud.proto`, package `fraud.v1`) as the only inter-service contract between wallet and fraud:

* Protobuf binary serialization: smaller payloads and faster (de)serialization than JSON on the hot path.
* Strongly typed contract versioned by package (`fraud.v1`), additive evolution only — field numbers are never reused.
* Money expressed as minor-unit decimal strings (integer cents) to avoid floating-point artifacts.
* Explicit call deadlines (300ms) enforced client-side; gRPC status codes (`INVALID_ARGUMENT`, `INTERNAL`, `UNAVAILABLE`) carry failure semantics instead of ad-hoc HTTP payloads.
* Code generation shared via the `grpc-contracts` Maven module consumed by both services.

## Consequences

* (+) Low-latency, type-safe intercom with generated stubs in both services.
* (+) Deadline propagation is native; retries and circuit breaking compose cleanly with status codes.
* (–) gRPC is harder to inspect with curl/browser tooling; debuggability relies on reflection tooling or test stubs (mitigated by the in-process integration test).
* (–) Additional build complexity (protoc plugin, code generation) localized in the contracts module.
* REST remains the public surface of the wallet service; gRPC is internal only.
