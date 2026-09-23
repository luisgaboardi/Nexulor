# ADR-010: OAuth2 Resource Server with JWT at the API Gateway

- **Status:** Accepted
- **Date:** 2026-09-28
- **Phase:** 4
- **Deciders:** Core engineering

## Context

PRD section 2.1 requires the API Gateway to apply security policies,
validating JWT tokens as an OAuth2 Resource Server. Until now the gateway
forwarded every request unauthenticated; financial mutations (wallet
creation, credits, transfers) were open to any caller with network access.

## Decision

The gateway becomes the **single OAuth2 resource server** for the external
surface:

1. **Spring Security (WebFlux)** with `oauth2ResourceServer().jwt()` — the
   `ReactiveJwtDecoder` bean is resolved from the context, so the security
   rules never change between environments:
   - **Local/dev (`jwt-local` profile):** HS256 tokens signed with a shared
     secret (≥ 256 bits, Nimbus-enforced), minted with the bash helper in
     the README. Keeps the showcase runnable without an authorization
     server.
   - **Production:** no profile; set
     `spring.security.oauth2.resourceserver.jwt.issuer-uri` and Boot
     auto-configures a JWKS-based RS256 decoder against the authorization
     server. Only the decoder bean differs.
2. **Policy:** every financial mutation requires a valid JWT —
   `POST /api/v1/wallets`, `POST /api/v1/wallets/*/credits`,
   `POST /api/v1/transfers`. Public surface stays read-only: actuator,
   GraphiQL, `POST /graphql` (queries), and all GET routes. Failures return
   `401` with `WWW-Authenticate: Bearer`.
3. **Downstream services remain unprotected** by design: they live on the
   internal network and trust the gateway. This is the standard edge
   security pattern; inter-service auth (mTLS) is a possible future
   hardening, out of scope here.

## Alternatives considered

- **Per-service resource servers:** repeats configuration three times and
  leaves the edge open; PRD explicitly assigns security to the gateway.
- **Opaque tokens / introspection:** adds a network hop per request; JWT
  validation is local and stateless.
- **Fail-closed for everything:** breaks the public showcase (GraphiQL,
  health) that makes the portfolio demonstrable without setup.

## Consequences

- External callers must obtain a JWT; local runs use the documented bash
  helper (shared demo secret) — acceptable for a showcase, clearly marked as
  non-production.
- Downstream trust boundary now ends at the gateway; a future zero-trust
  iteration would add mTLS or service tokens between services.
- The policy is testable end to end (401/401/201 matrix covered by unit
  tests with a minted token and validated against the compose stack).
