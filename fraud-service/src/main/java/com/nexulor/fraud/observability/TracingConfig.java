package com.nexulor.fraud.observability;

import brave.grpc.GrpcTracing;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Distributed tracing wiring (Phase 4, ADR-011). Zipkin reporting and the
 * Brave/Micrometer bridge are auto-configured by Boot; this class only adds
 * the Brave gRPC instrumentation used by the server interceptor that joins
 * the caller's trace.
 */
@Configuration
public class TracingConfig {

    /** Provides server interceptors for incoming gRPC calls. */
    @Bean
    GrpcTracing grpcTracing(brave.Tracing tracing) {
        return GrpcTracing.newBuilder(tracing).build();
    }
}
