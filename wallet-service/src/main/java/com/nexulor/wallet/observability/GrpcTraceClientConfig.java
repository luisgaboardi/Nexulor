package com.nexulor.wallet.observability;

import brave.grpc.GrpcTracing;
import io.grpc.ClientInterceptor;
import net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Registers Brave's gRPC client interceptor globally (profile grpc-fraud):
 * every outgoing call to the fraud service carries the W3C trace context.
 */
@Configuration
@Profile("grpc-fraud")
public class GrpcTraceClientConfig {

    @Bean
    @GrpcGlobalClientInterceptor
    ClientInterceptor braveGrpcClientInterceptor(GrpcTracing grpcTracing) {
        return grpcTracing.newClientInterceptor();
    }
}
