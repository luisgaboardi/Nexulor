package com.nexulor.gateway.config;

/**
 * Shared secret for the {@code jwt-local} profile. Length ≥ 256 bits is
 * mandatory for HS256 (enforced by Nimbus). This is a demo/showcase secret
 * for local runs only; production must use a real authorization server via
 * {@code issuer-uri} (JWKS/RS256).
 */
public final class LocalJwtSecrets {

    /** local-demo-secret-change-me-0123456789abcdef (≥ 256 bits). */
    public static final String DEMO_SECRET =
            "local-demo-secret-change-me-0123456789abcdef";

    private LocalJwtSecrets() {
    }
}
