package com.nexulor.wallet.api;

import com.nexulor.wallet.application.TransferApplicationService;
import com.nexulor.wallet.application.TransferCommand;
import com.nexulor.wallet.domain.Transfer;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class TransferController {

    private final TransferApplicationService transferApplicationService;

    public TransferController(TransferApplicationService transferApplicationService) {
        this.transferApplicationService = transferApplicationService;
    }

    /**
     * Financial mutation with mandatory idempotency (PRD section 3): the
     * {@code Idempotency-Key} header deduplicates replays across instances;
     * missing header is a 400, replayed key returns the original 201, and a
     * reused key with a different payload is a 409.
     */
    @PostMapping("/transfers")
    public ResponseEntity<ApiResponses.TransferResponse> transfer(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ApiRequests.TransferRequest request) {
        Transfer transfer = transferApplicationService.transfer(
                new TransferCommand(
                        request.sourceWalletId(),
                        request.destinationWalletId(),
                        request.amount(),
                        request.currency().toUpperCase()),
                idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponses.TransferResponse.from(transfer));
    }

    @GetMapping("/transfers/{transferId}")
    public ApiResponses.TransferResponse get(@PathVariable UUID transferId) {
        return ApiResponses.TransferResponse.from(transferApplicationService.getTransfer(transferId));
    }

    @GetMapping("/wallets/{walletId}/transfers")
    public List<ApiResponses.TransferResponse> listByWallet(@PathVariable UUID walletId) {
        return transferApplicationService.listByWallet(walletId).stream()
                .map(ApiResponses.TransferResponse::from)
                .toList();
    }
}
