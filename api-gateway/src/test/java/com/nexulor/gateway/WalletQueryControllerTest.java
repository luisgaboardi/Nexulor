package com.nexulor.gateway;

import com.nexulor.gateway.client.DownstreamDtos;
import com.nexulor.gateway.client.StatementClient;
import com.nexulor.gateway.client.WalletClient;
import com.nexulor.gateway.exception.DownstreamCallException;
import com.nexulor.gateway.graphql.WalletQueryController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the GraphQL aggregation logic: parallel fan-out, stitching,
 * and graceful degradation when the statement read model is unavailable.
 */
class WalletQueryControllerTest {

    private WalletClient walletClient;
    private StatementClient statementClient;
    private WalletQueryController controller;

    private final UUID walletId = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private final UUID ownerId = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    @BeforeEach
    void setUp() {
        walletClient = mock(WalletClient.class);
        statementClient = mock(StatementClient.class);
        controller = new WalletQueryController(walletClient, statementClient);
    }

    private DownstreamDtos.WalletResponse wallet() {
        return new DownstreamDtos.WalletResponse(
                walletId, ownerId, new BigDecimal("100.50"), "BRL",
                Instant.parse("2026-09-28T10:00:00Z"), Instant.parse("2026-09-28T11:00:00Z"));
    }

    private DownstreamDtos.TransferResponse transfer() {
        return new DownstreamDtos.TransferResponse(
                UUID.randomUUID(), walletId, UUID.randomUUID(),
                new BigDecimal("25.00"), "BRL", "COMPLETED",
                Instant.parse("2026-09-28T12:00:00Z"), Instant.parse("2026-09-28T12:00:01Z"));
    }

    private DownstreamDtos.StatementResponse statementEntry() {
        return new DownstreamDtos.StatementResponse(
                "evt-1", walletId, UUID.randomUUID(), UUID.randomUUID(),
                "DEBIT", new BigDecimal("25.00"), "BRL", Instant.parse("2026-09-28T12:00:02Z"));
    }

    @Test
    void walletByOwnerMapsToViewModelWithPlainStringMoney() {
        when(walletClient.findByOwner(ownerId.toString())).thenReturn(Mono.just(wallet()));

        StepVerifier.create(controller.walletByOwner(ownerId.toString()))
                .assertNext(view -> {
                    assertThat(view.id()).isEqualTo(walletId.toString());
                    assertThat(view.balance()).isEqualTo("100.50");
                    assertThat(view.currency()).isEqualTo("BRL");
                })
                .verifyComplete();
    }

    @Test
    void walletSummaryStitchesWalletTransfersAndStatement() {
        when(walletClient.findByOwner(ownerId.toString())).thenReturn(Mono.just(wallet()));
        when(walletClient.listTransfers(walletId.toString())).thenReturn(Flux.just(transfer()));
        when(statementClient.statementList(walletId.toString()))
                .thenReturn(Mono.just(List.of(statementEntry())));

        StepVerifier.create(controller.walletSummary(ownerId.toString()))
                .assertNext(summary -> {
                    assertThat(summary.wallet().balance()).isEqualTo("100.50");
                    assertThat(summary.recentTransfers()).hasSize(1);
                    assertThat(summary.recentTransfers().get(0).amount()).isEqualTo("25.00");
                    assertThat(summary.statement()).hasSize(1);
                    assertThat(summary.statement().get(0).direction()).isEqualTo("DEBIT");
                    assertThat(summary.partialErrors()).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    void walletSummaryDegradesGracefullyWhenStatementIsUnavailable() {
        when(walletClient.findByOwner(ownerId.toString())).thenReturn(Mono.just(wallet()));
        when(walletClient.listTransfers(walletId.toString())).thenReturn(Flux.empty());
        when(statementClient.statementList(anyString())).thenReturn(
                Mono.error(new DownstreamCallException("STATEMENT_UNAVAILABLE", "down", null)));

        StepVerifier.create(controller.walletSummary(ownerId.toString()))
                .assertNext(summary -> {
                    assertThat(summary.wallet()).isNotNull();
                    assertThat(summary.recentTransfers()).isEmpty();
                    assertThat(summary.statement()).isEmpty();
                    assertThat(summary.partialErrors()).containsExactly("STATEMENT_UNAVAILABLE");
                })
                .verifyComplete();
    }

    @Test
    void walletByOwnerPropagatesDownstreamFailure() {
        when(walletClient.findByOwner(ownerId.toString())).thenReturn(
                Mono.error(new DownstreamCallException("WALLET_UNAVAILABLE", "down", null)));

        StepVerifier.create(controller.walletByOwner(ownerId.toString()))
                .expectError(DownstreamCallException.class)
                .verify();
    }
}
