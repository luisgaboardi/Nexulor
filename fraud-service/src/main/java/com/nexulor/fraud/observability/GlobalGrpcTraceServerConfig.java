package com.nexulor.fraud.observability;

import brave.grpc.GrpcTracing;
import io.grpc.ServerInterceptor;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers Brave's gRPC server interceptor globally: incoming calls carry
 * the caller's W3C trace context, so the fraud span joins the wallet's trace.
 */
@Configuration
public class GlobalGrpcTraceServerConfig {

    @Bean
    @GrpcGlobalServerInterceptor
    ServerInterceptor braveGrpcServerInterceptor(GrpcTracing grpcTracing) {
        return grpcTracing.newServerInterceptor();
    }
}
