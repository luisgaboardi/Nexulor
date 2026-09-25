package com.nexulor.wallet.observability;

import brave.grpc.GrpcTracing;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Distributed tracing wiring (Phase 4, ADR-011). Zipkin reporting and the
 * Brave/Micrometer bridge are auto-configured by Boot from the
 * micrometer-tracing-bridge-brave + zipkin-reporter-brave dependencies and
 * management.zipkin.tracing.endpoint; this class only adds the Brave gRPC
 * instrumentation used by the fraud client interceptors.
 */
@Configuration
public class TracingConfig {

    /** Provides client interceptors for the fraud gRPC calls. */
    @Bean
    GrpcTracing grpcTracing(brave.Tracing tracing) {
        return GrpcTracing.newBuilder(tracing).build();
    }
}
