package com.nexulor.gateway.client;

import com.nexulor.gateway.config.GatewayProperties;
import com.nexulor.gateway.exception.DownstreamCallException;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;

/**
 * REST client for the notification service statement read model (CQRS).
 */
@Component
public class StatementClient {

    private final WebClient webClient;
    private final int maxRetries;
    private final Duration retryBackoff;

    public StatementClient(WebClient downstreamWebClient, GatewayProperties properties) {
        this.webClient = downstreamWebClient.mutate()
                .baseUrl(properties.notificationService().baseUrl())
                .build();
        this.maxRetries = properties.downstream().retries();
        this.retryBackoff = Duration.ofMillis(100);
    }

    public Flux<DownstreamDtos.StatementResponse> statement(String walletId) {
        return webClient.get()
                .uri("/api/v1/wallets/{walletId}/statement", walletId)
                .retrieve()
                .bodyToFlux(DownstreamDtos.StatementResponse.class)
                .retryWhen(retrySpec())
                .onErrorMap(e -> !(e instanceof DownstreamCallException),
                        e -> new DownstreamCallException("STATEMENT_UNAVAILABLE", "Statement service call failed", e));
    }

    public Mono<List<DownstreamDtos.StatementResponse>> statementList(String walletId) {
        return statement(walletId).collectList();
    }

    private Retry retrySpec() {
        return Retry.backoff(maxRetries, retryBackoff).maxBackoff(retryBackoff.multipliedBy(4));
    }
}
