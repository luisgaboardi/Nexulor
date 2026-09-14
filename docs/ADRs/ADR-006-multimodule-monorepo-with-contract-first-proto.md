# ADR-006: Multi-Module Monorepo with Contract-First Proto Workflow

## Status

Accepted (Phase 2)

## Context

Phase 2 extracts the Fraud Detection Service while the project remains a portfolio showcase. The team must decide between separate repositories versus a single repository, and between sharing generated stubs via a published artifact versus building them from source.

## Decision

* Single Git repository with a multi-module Maven build:
  * `grpc-contracts` — proto files + generated stubs (contract-first, versioned by proto package `fraud.v1`).
  * `wallet-service` — financial core (Phase 1 code, plus the fraud client).
  * `fraud-service` — rules engine + gRPC server + MongoDB audit.
* The proto file is the single source of truth: stubs are generated at build time by `protobuf-maven-plugin`; generated sources are never committed.
* Contract evolution is additive only (new fields, never reused/renumbered field numbers) within `fraud.v1`; breaking changes require a new package version (`fraud.v2`).

## Consequences

* (+) Atomic cross-service contract changes in one pull request; simple review story for recruiters.
* (+) No artifact repository needed; the reactor resolves `grpc-contracts` locally.
* (–) CI must build the contracts module before dependents (handled by `mvn -pl <svc> -am`).
* (–) Repository grows with each future service; extraction to separate repos remains possible later since modules are self-contained.
