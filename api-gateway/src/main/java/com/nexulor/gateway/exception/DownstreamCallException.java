package com.nexulor.gateway.exception;

/**
 * Raised when a downstream call fails after retries. The {@code code}
 * distinguishes "not found" from "unavailable" so the GraphQL layer can map
 * both to meaningful errors.
 */
public class DownstreamCallException extends RuntimeException {

    private final String code;

    public DownstreamCallException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
