package com.nexulor.gateway.client;

import com.nexulor.gateway.config.GatewayProperties;
import com.nexulor.gateway.exception.DownstreamCallException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;

/**
 * REST client for the wallet service (financial core). Used by the GraphQL
 * aggregation layer; the gateway's REST routes proxy to this service directly
 * via Spring Cloud Gateway.
 */
@Component
public class WalletClient {

    private static final ParameterizedTypeReference<List<DownstreamDtos.TransferResponse>> TRANSFER_LIST =
            new ParameterizedTypeReference<>() {
            };

    private final WebClient webClient;
    private final int maxRetries;
    private final Duration retryBackoff;

    public WalletClient(WebClient downstreamWebClient, GatewayProperties properties) {
        this.webClient = downstreamWebClient.mutate()
                .baseUrl(properties.walletService().baseUrl())
                .build();
        this.maxRetries = properties.downstream().retries();
        this.retryBackoff = Duration.ofMillis(100);
    }

    public Mono<DownstreamDtos.WalletResponse> findByOwner(String ownerId) {
        return webClient.get()
                .uri("/api/v1/wallets/by-owner/{ownerId}", ownerId)
                .retrieve()
                .bodyToMono(DownstreamDtos.WalletResponse.class)
                .retryWhen(retrySpec())
                .onErrorMap(WebClientResponseException.NotFound.class,
                        e -> new DownstreamCallException("WALLET_NOT_FOUND", "No wallet for owner " + ownerId, e))
                .onErrorMap(e -> !(e instanceof DownstreamCallException),
                        e -> new DownstreamCallException("WALLET_UNAVAILABLE", "Wallet service call failed", e));
    }

    public Flux<DownstreamDtos.TransferResponse> listTransfers(String walletId) {
        return webClient.get()
                .uri("/api/v1/wallets/{walletId}/transfers", walletId)
                .retrieve()
                .bodyToFlux(DownstreamDtos.TransferResponse.class)
                .retryWhen(retrySpec())
                .onErrorMap(e -> !(e instanceof DownstreamCallException),
                        e -> new DownstreamCallException("WALLET_UNAVAILABLE", "Wallet service call failed", e));
    }

    private Retry retrySpec() {
        return Retry.backoff(maxRetries, retryBackoff).maxBackoff(retryBackoff.multipliedBy(4));
    }
}
