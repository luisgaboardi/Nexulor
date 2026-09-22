# ADR-009: API Gateway with Spring Cloud Gateway and GraphQL Aggregation

- **Status:** Accepted
- **Date:** 2026-09-28
- **Phase:** 4
- **Deciders:** Core engineering

## Context

The PRD (section 2.1) requires a single entry point for external clients that
routes requests, aggregates data, and applies security policies. Today clients
must talk to two different services: the wallet service (`:8080`) for the
financial core and the notification service (`:8082`) for the CQRS statement
read model. A typical client need — "show me a wallet with its recent
transfers and its statement" — currently requires three calls to two hosts,
and the caller must know the topology.

## Decision

Introduce an **`api-gateway` module** (Java 21, Spring Boot 3.4) with:

1. **Spring Cloud Gateway (WebFlux/Netty)** for REST routing:
   - `/api/v1/wallets/*/statement` → notification service (matched *before*
     the generic wallet route; both live under `/api/v1/wallets/**` but belong
     to different services).
   - `/api/v1/wallets/**`, `/api/v1/transfers/**` → wallet service.
2. **Spring GraphQL** for aggregation on `POST /graphql` (GraphiQL enabled
   for the showcase):
   - `walletByOwner(ownerId)` — proxied to the wallet service.
   - `walletSummary(ownerId)` — parallel fan-out to wallet + transfers +
     statement, stitched into one response. Money travels as `String` to
     preserve `BigDecimal` precision end to end.
3. **Bounded downstream calls** (PRD section 1: assume the network fails):
   every gateway-initiated call has a configurable timeout (`gateway.downstream.timeout`,
   default 2s) and bounded retries with exponential backoff.

**Graceful degradation:** in `walletSummary`, the wallet is the source of
truth and the statement is an eventual read model. If the statement service
is unreachable after retries, the summary still returns with the statement
portion empty and the failure code reported in `partialErrors`
(`STATEMENT_UNAVAILABLE`). A wallet failure fails the whole query — there is
nothing to degrade to.

**Downstream failures** are mapped by a `DataFetcherExceptionResolver`:
unknown wallet → `NOT_FOUND` with code `WALLET_NOT_FOUND`; unreachable
service → `INTERNAL_ERROR` with machine-readable `extensions.code`
(`WALLET_UNAVAILABLE` / `STATEMENT_UNAVAILABLE`).

## Alternatives considered

- **Federation / schema stitching (Apollo-style):** powerful, but each
  downstream service would need a GraphQL server; overkill for two services
  and breaks "REST for commands" simplicity.
- **Gateway calls downstream over GraphQL only:** rejects the REST routes the
  PRD explicitly keeps; commands (POST transfers) remain plain REST through
  the router.
- **No aggregation (BFF-less):** clients keep the 3-call/2-host problem.

## Consequences

- External clients need only `:8088` (gateway). Downstream ports remain for
  inter-service traffic and local debugging.
- Security (OAuth2 resource server, PRD 2.1) now has a single enforcement
  point to be added in a later iteration of Phase 4.
- The gateway adds one hop of latency for proxied REST calls; acceptable for
  a showcase and standard for edge routing.
- Retry budget on the gateway is deliberately conservative (1 retry) to avoid
  amplifying load during downstream incidents.
