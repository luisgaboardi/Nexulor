package com.nexulor.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * API Gateway — single entry point for external clients (PRD section 2.1).
 *
 * <p>WebFlux/Netty runtime with Spring Cloud Gateway route definitions plus a
 * GraphQL endpoint that aggregates the wallet core (REST) and the statement
 * read model (notification service) into a single query.</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
