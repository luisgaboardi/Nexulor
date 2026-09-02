# ADR-000: Record Architecture Decisions

## Status

Accepted

## Context

Nexulor is a multi-phase distributed payments showcase. Structural choices (consistency model, inter-service protocols, messaging) must remain auditable for recruiters and future contributors.

## Decision

We will maintain Architecture Decision Records under `/docs/ADRs` in English, using a lightweight template: Context, Decision, Consequences.

## Consequences

* Architectural rationale is versioned alongside code.
* Agents and developers must add or update an ADR when introducing complex structural changes.
* Early phases may accept transitional decisions that later ADRs supersede.
