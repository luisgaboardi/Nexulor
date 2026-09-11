package com.nexulor.wallet.infrastructure.fraud;

import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Opens the gRPC channel (TCP + HTTP/2 handshake) at startup by calling the
 * standard gRPC health check served by the fraud service. Without this, the
 * first transfer pays the connection setup; on cold environments (e.g.
 * nested container networks) that can exceed the fraud deadline and fail
 * the very first request (fail-closed).
 */
@Component
@Profile("grpc-fraud")
public class FraudChannelWarmUp {

    private static final Logger log = LoggerFactory.getLogger(FraudChannelWarmUp.class);

    @GrpcClient("fraudService")
    private HealthGrpc.HealthBlockingStub healthStub;

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        try {
            HealthCheckResponse reply = healthStub
                    .withDeadlineAfter(5, java.util.concurrent.TimeUnit.SECONDS)
                    .check(HealthCheckRequest.newBuilder().build());
            log.info("fraud gRPC channel warmed up; health={}", reply.getStatus());
        } catch (Exception e) {
            // Non-fatal: the transfer path retries and the channel is reused
            // once the fraud service becomes reachable.
            log.warn("fraud gRPC channel warm-up failed (will retry on first transfer): {}", e.toString());
        }
    }
}
