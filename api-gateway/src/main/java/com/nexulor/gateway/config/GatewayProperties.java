package com.nexulor.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Gateway settings: downstream base URLs and resilience budget for calls the
 * gateway itself makes (GraphQL aggregation). PRD section 1: assume the
 * network fails — every downstream call is bounded by a timeout and retries.
 */
@ConfigurationProperties("gateway")
public record GatewayProperties(
        Service walletService,
        Service notificationService,
        Downstream downstream) {

    public record Service(String baseUrl) {
    }

    public record Downstream(Duration timeout, int retries) {
    }
}
