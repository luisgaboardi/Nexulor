# ADR-007: Cross-Instance Velocity Counting with Redis (Fail-Open)

## Status

Accepted (Phase 3)

## Context

The R2 velocity rule (Phase 2, ADR-005) counted per-instance transfers with Caffeine. With multiple fraud-service instances behind a load balancer, each instance sees only a fraction of the traffic, so the effective limit multiplied by the instance count — the rule degraded to a best-effort signal instead of an enforcement point.

## Decision

* Extract the counter behind a port (`VelocityCounterPort`). Production wiring is `RedisVelocityCounter` (profile `redis-velocity`): one key per source wallet (`fraud:velocity:{id}`), incremented atomically by Lua (INCR + PEXPIRE set only on key creation, so the window restarts cleanly on the first hit).
* The local Caffeine counter remains the default (profile `!redis-velocity`) for standalone runs, preserving Phase 2 window-expiry semantics.
* The rule **fails open**: if the counter infrastructure is unreachable, the rule is skipped and R1/R3 still run. Rationale: fraud evaluation must answer the wallet within its deadline (fail-closed is already enforced end-to-end for *service* unavailability by ADR-005); a degraded velocity signal must not reject every transfer, and the miss is observable via the WARN log and absent audit entries.

## Consequences

* The velocity limit is now enforced cluster-wide, not per instance.
* Fraud-service adds a Redis dependency under the `redis-velocity` profile; Redis unavailability weakens R2 only (documented, logged) instead of breaking evaluation.
* Counter TTL equals the configured window; no cleanup job is needed.
