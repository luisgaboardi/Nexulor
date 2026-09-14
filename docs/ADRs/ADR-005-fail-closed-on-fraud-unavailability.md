# ADR-005: Fail-Closed on Fraud Unavailability and REVIEW Semantics

## Status

Accepted (Phase 2)

## Context

The wallet service calls the Fraud Detection Service synchronously before committing a P2P transfer. The fraud service can reject (business decision) or become unreachable (timeout after retries, open circuit breaker). The system must decide what happens when no risk opinion is available, and how to treat a `REVIEW` decision, for which no manual-review workflow exists in Phase 2.

## Decision

* **Fail-closed:** if fraud cannot be reached after the resilience chain (300ms deadline → 3 attempts on transport errors → circuit breaker), the transfer is refused with `503 FRAUD_UNAVAILABLE`. Money is never moved without a risk opinion.
* Fraud is evaluated **before any wallet lock or balance mutation**: rejections and outages never pay lock contention costs or hold transactions open.
* **`REVIEW` is treated as APPROVE in Phase 2**, loudly logged, because no manual-review workflow exists. The decision is preserved in the contract so Phase 4 can introduce a review queue without a wire change.
* A `NoopFraudAdapter` (default profile) approves everything for standalone/local runs of the wallet alone; it is only active when the `grpc-fraud` profile is absent and logs a prominent warning at startup.
* Fraud rejections surface to clients as `422 FRAUD_REJECTED`; unavailability as `503 FRAUD_UNAVAILABLE`.

## Consequences

* (+) No unauthorized money movement during fraud outages — the conservative, defensible default for a payments core.
* (+) Cheap early rejection: no locks acquired for fraud-refused transfers.
* (–) Fraud becomes a hard dependency on the transfer path; its availability caps transfer availability (mitigated by retry + circuit breaker and observable via metrics).
* (–) `REVIEW`-as-approve is a temporary risk posture, accepted explicitly and revisited when a review workflow exists.
