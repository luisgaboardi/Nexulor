package com.nexulor.wallet.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public final class ApiRequests {

    private ApiRequests() {
    }

    public record CreateWalletRequest(
            @NotNull UUID ownerId,
            @NotBlank @Size(min = 3, max = 3) String currency) {
    }

    public record CreditWalletRequest(
            @NotNull @DecimalMin(value = "0.0001", inclusive = true) BigDecimal amount,
            @NotBlank @Size(min = 3, max = 3) String currency) {
    }

    public record TransferRequest(
            @NotNull UUID sourceWalletId,
            @NotNull UUID destinationWalletId,
            @NotNull @DecimalMin(value = "0.0001", inclusive = true) BigDecimal amount,
            @NotBlank @Size(min = 3, max = 3) String currency) {
    }
}
