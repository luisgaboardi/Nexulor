package com.nexulor.gateway.graphql;

import com.nexulor.gateway.client.DownstreamDtos;
import com.nexulor.gateway.client.StatementClient;
import com.nexulor.gateway.client.WalletClient;
import com.nexulor.gateway.exception.DownstreamCallException;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * GraphQL aggregation over the two downstream REST services (PRD 2.1):
 * a single {@code walletSummary} query fans out to wallet-core data and the
 * CQRS statement read model in parallel and stitches the result.
 */
@Controller
public class WalletQueryController {

    private final WalletClient walletClient;
    private final StatementClient statementClient;

    public WalletQueryController(WalletClient walletClient, StatementClient statementClient) {
        this.walletClient = walletClient;
        this.statementClient = statementClient;
    }

    @QueryMapping
    public Mono<WalletView> walletByOwner(@Argument String ownerId) {
        return walletClient.findByOwner(ownerId)
                .map(WalletView::from);
    }

    /**
     * Aggregated summary: fan-out to wallet + transfers + statement in
     * parallel, join, then stitch. If the statement service is unavailable the
     * summary still returns, with the statement portion reported as an entry
     * in {@code partialErrors} (degrades gracefully — the wallet is the source
     * of truth, the statement is an eventual read model).
     */
    @QueryMapping
    public Mono<WalletSummaryView> walletSummary(@Argument String ownerId) {
        return walletClient.findByOwner(ownerId)
                .flatMap(wallet -> {
                    String walletId = wallet.id().toString();
                    Mono<List<TransferView>> transfers = walletClient.listTransfers(walletId)
                            .map(TransferView::from)
                            .collectList()
                            .onErrorResume(DownstreamCallException.class,
                                    e -> Mono.just(List.<TransferView>of()));
                    Mono<StatementPartial> statement = statementClient.statementList(walletId)
                            .map(entries -> new StatementPartial(
                                    entries.stream().map(StatementView::from).toList(), null))
                            .onErrorResume(DownstreamCallException.class,
                                    e -> Mono.just(new StatementPartial(List.<StatementView>of(), e.code())));

                    return Mono.zip(statement, transfers)
                            .map(tuple -> WalletSummaryView.from(
                                    wallet, tuple.getT2(), tuple.getT1().entries(), tuple.getT1().errorCode()));
                });
    }

    /**
     * Statement fan-out result that keeps both the entries and a possible
     * failure code, so a single downstream call feeds the graceful-degradation
     * path (statement is an eventual read model, never blocks the summary).
     */
    private record StatementPartial(List<StatementView> entries, String errorCode) {
    }

    // ---- GraphQL view models (String-typed money preserves precision) ----

    public record WalletView(
            String id,
            String ownerId,
            String balance,
            String currency,
            String createdAt,
            String updatedAt) {

        static WalletView from(DownstreamDtos.WalletResponse w) {
            return new WalletView(
                    w.id().toString(),
                    w.ownerId().toString(),
                    w.balance().toPlainString(),
                    w.currency(),
                    w.createdAt().toString(),
                    w.updatedAt().toString());
        }
    }

    public record TransferView(
            String id,
            String sourceWalletId,
            String destinationWalletId,
            String amount,
            String currency,
            String status,
            String createdAt,
            String completedAt) {

        static TransferView from(DownstreamDtos.TransferResponse t) {
            return new TransferView(
                    t.id().toString(),
                    t.sourceWalletId().toString(),
                    t.destinationWalletId().toString(),
                    t.amount().toPlainString(),
                    t.currency(),
                    t.status(),
                    t.createdAt().toString(),
                    t.completedAt() == null ? null : t.completedAt().toString());
        }
    }

    public record StatementView(
            String id,
            String walletId,
            String transferId,
            String counterpartyWalletId,
            String direction,
            String amount,
            String currency,
            String completedAt) {

        static StatementView from(DownstreamDtos.StatementResponse s) {
            return new StatementView(
                    s.id(),
                    s.walletId().toString(),
                    s.transferId().toString(),
                    s.counterpartyWalletId() == null ? null : s.counterpartyWalletId().toString(),
                    s.direction(),
                    s.amount().toPlainString(),
                    s.currency(),
                    s.completedAt().toString());
        }
    }

    public record WalletSummaryView(
            WalletView wallet,
            List<TransferView> recentTransfers,
            List<StatementView> statement,
            List<String> partialErrors) {

        static WalletSummaryView from(
                DownstreamDtos.WalletResponse wallet,
                List<TransferView> transfers,
                List<StatementView> statement,
                String statementErrorCode) {
            List<String> errors = statementErrorCode == null || statementErrorCode.isBlank()
                    ? List.of()
                    : List.of(statementErrorCode);
            return new WalletSummaryView(
                    WalletView.from(wallet),
                    transfers,
                    statement,
                    errors);
        }
    }
}
