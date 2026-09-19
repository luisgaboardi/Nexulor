package com.nexulor.notification.api;

import com.nexulor.notification.readmodel.StatementEntryDocument;
import com.nexulor.notification.readmodel.StatementEntryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class StatementController {

    private final StatementEntryRepository repository;

    public StatementController(StatementEntryRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/wallets/{walletId}/statement")
    public List<StatementResponse> statement(@PathVariable UUID walletId) {
        return repository.findByWalletIdOrderByCompletedAtDesc(walletId).stream()
                .map(StatementResponse::from)
                .toList();
    }

    public record StatementResponse(
            String id,
            UUID walletId,
            UUID transferId,
            UUID counterpartyWalletId,
            String direction,
            java.math.BigDecimal amount,
            String currency,
            java.time.Instant completedAt) {

        static StatementResponse from(StatementEntryDocument doc) {
            return new StatementResponse(
                    doc.getId(),
                    doc.getWalletId(),
                    doc.getTransferId(),
                    doc.getCounterpartyWalletId(),
                    doc.getDirection(),
                    doc.getAmount(),
                    doc.getCurrency(),
                    doc.getCompletedAt());
        }
    }
}
