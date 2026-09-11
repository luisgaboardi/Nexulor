package com.nexulor.wallet.api;

import com.nexulor.wallet.application.TransferApplicationService;
import com.nexulor.wallet.application.TransferApplicationService.FraudRejectedException;
import com.nexulor.wallet.domain.CurrencyMismatchException;
import com.nexulor.wallet.domain.DomainException;
import com.nexulor.wallet.domain.FraudUnavailableException;
import com.nexulor.wallet.domain.InsufficientFundsException;
import com.nexulor.wallet.domain.InvalidTransferException;
import com.nexulor.wallet.domain.TransferNotFoundException;
import com.nexulor.wallet.domain.WalletAlreadyExistsException;
import com.nexulor.wallet.domain.WalletNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(WalletNotFoundException.class)
    public ResponseEntity<ApiResponses.ErrorResponse> handleWalletNotFound(WalletNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "WALLET_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(TransferNotFoundException.class)
    public ResponseEntity<ApiResponses.ErrorResponse> handleTransferNotFound(TransferNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "TRANSFER_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(WalletAlreadyExistsException.class)
    public ResponseEntity<ApiResponses.ErrorResponse> handleWalletExists(WalletAlreadyExistsException ex) {
        return error(HttpStatus.CONFLICT, "WALLET_ALREADY_EXISTS", ex.getMessage());
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ApiResponses.ErrorResponse> handleInsufficientFunds(InsufficientFundsException ex) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_FUNDS", ex.getMessage());
    }

    @ExceptionHandler(FraudRejectedException.class)
    public ResponseEntity<ApiResponses.ErrorResponse> handleFraudRejected(FraudRejectedException ex) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, "FRAUD_REJECTED", ex.getMessage());
    }

    @ExceptionHandler(FraudUnavailableException.class)
    public ResponseEntity<ApiResponses.ErrorResponse> handleFraudUnavailable(FraudUnavailableException ex) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "FRAUD_UNAVAILABLE", ex.getMessage());
    }

    @ExceptionHandler({InvalidTransferException.class, CurrencyMismatchException.class})
    public ResponseEntity<ApiResponses.ErrorResponse> handleInvalidTransfer(DomainException ex) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_TRANSFER", ex.getMessage());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponses.ErrorResponse> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return error(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                "wallet was modified concurrently; retry the operation");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponses.ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponses.ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", ex.getMessage());
    }

    private String formatFieldError(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }

    private ResponseEntity<ApiResponses.ErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiResponses.ErrorResponse(code, message));
    }
}
