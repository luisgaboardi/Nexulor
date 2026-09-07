package com.nexulor.wallet.api;

import com.nexulor.wallet.application.CreateWalletCommand;
import com.nexulor.wallet.application.CreditWalletCommand;
import com.nexulor.wallet.application.WalletApplicationService;
import com.nexulor.wallet.domain.Wallet;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallets")
public class WalletController {

    private final WalletApplicationService walletApplicationService;

    public WalletController(WalletApplicationService walletApplicationService) {
        this.walletApplicationService = walletApplicationService;
    }

    @PostMapping
    public ResponseEntity<ApiResponses.WalletResponse> create(
            @Valid @RequestBody ApiRequests.CreateWalletRequest request) {
        Wallet wallet = walletApplicationService.createWallet(
                new CreateWalletCommand(request.ownerId(), request.currency().toUpperCase()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponses.WalletResponse.from(wallet));
    }

    @GetMapping("/{walletId}")
    public ApiResponses.WalletResponse get(@PathVariable UUID walletId) {
        return ApiResponses.WalletResponse.from(walletApplicationService.getWallet(walletId));
    }

    @GetMapping("/by-owner/{ownerId}")
    public ApiResponses.WalletResponse getByOwner(@PathVariable UUID ownerId) {
        return ApiResponses.WalletResponse.from(walletApplicationService.getWalletByOwner(ownerId));
    }

    @PostMapping("/{walletId}/credits")
    public ApiResponses.WalletResponse credit(
            @PathVariable UUID walletId,
            @Valid @RequestBody ApiRequests.CreditWalletRequest request) {
        Wallet wallet = walletApplicationService.credit(
                new CreditWalletCommand(walletId, request.amount(), request.currency().toUpperCase()));
        return ApiResponses.WalletResponse.from(wallet);
    }
}
