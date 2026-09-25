# ADR-011: Distributed tracing with Micrometer Tracing (Brave) and Zipkin

- **Status:** Accepted
- **Date:** 2026-09-29
- **Phase:** 4
- **Deciders:** Core engineering

## Context

The request path now crosses five services: gateway -> wallet -> fraud (gRPC)
-> Kafka -> notification. When latency spikes or a stage fails, per-service
logs cannot answer "where did this request spend its time" or "which stage
broke the chain". PRD section 4 requires OpenTelemetry-style distributed
tracing.

## Decision

Instrument every service with **Micrometer Tracing on the Brave bridge**
(`micrometer-tracing-bridge-brave` + `zipkin-reporter-brave`), reporting to
**Zipkin** in docker-compose. W3C (`traceparent`) propagation end to end:

- **HTTP (gateway, wallet, notification):** Boot auto-instruments MVC/WebFlux
  servers and route/WebClient clients. The gateway's aggregation WebClient is
  built via the Boot-managed `WebClient.Builder` so its calls join the trace.
- **gRPC (wallet -> fraud):** Brave's `grpc-instrumentation` interceptors
  registered through the devh starter's global interceptor hooks
  (`GrpcTracing.newClientInterceptor()` / `newServerInterceptor()`). The
  wallet runs the call on a virtual thread, so the parent context is
  explicitly re-scoped inside the worker (`currentTraceContext().maybeScope`)
  — without this the fraud span starts an orphan trace.
- **Kafka (wallet -> notification):** `spring.kafka.template.observation-enabled`
  injects trace headers at produce; `spring.kafka.listener.observation-enabled`
  extracts them at consume, stitching the async leg into the same trace.
  Custom observations (`transaction-completed.publish/.project`) wrap the
  domain-relevant work between the transport spans.

100% sampling for the showcase (`management.tracing.sampling.probability: 1.0`);
production would sample lower. Zipkin endpoint via `ZIPKIN_ENDPOINT` env
(default `localhost:9411`, compose sets `http://zipkin:9411`).

## Alternatives considered

- **OpenTelemetry SDK + collector:** vendor-neutral and the "industry
  default", but adds an agent/collector hop and a second API surface; Micrometer
  + Brave achieves the same end-to-end trace with zero custom code outside
  gRPC/Kafka glue and is the path Boot 3.x natively integrates. Migration to
  OTLP later only replaces the reporter.
- **Jaeger instead of Zipkin:** equivalent capability; Zipkin's UI/API is
  lighter for a showcase and Zipkin-compatible reporting is native to Brave.
- **Manual MDC correlation IDs only:** cheaper, but no timing tree, no
  service topology, no gap analysis.

## Consequences

- One trace answers: gateway auth + routing -> wallet transfer tx + idempotency
  -> fraud evaluation latency -> Kafka publish -> projection. Verified E2E:
  a single traceId spans all five services including the async Kafka leg.
- Span volume is high at 100% sampling (health checks create traces too);
  compose keeps Zipkin storage in-memory (non-durable, showcase only).
- The Brave bridge keeps Micrometer `Observation` APIs in application code,
  so future migration to the OTel backend is a reporter swap.
