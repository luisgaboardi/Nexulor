package com.nexulor.gateway.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Instant;
import java.util.Date;

/**
 * Gateway security policy (PRD 2.1): JWT-protected financial mutations,
 * public read-only showcase surface. Tests run with the local HS256 decoder
 * ({@code jwt-local} profile); the positive case mints a real HS256 token to
 * exercise the full decode + validate path.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("jwt-local")
class SecurityConfigTest {

    @Autowired
    WebTestClient webTestClient;

    @Test
    void actuatorHealthIsPublic() {
        webTestClient.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void createWalletWithoutTokenIsUnauthorized() {
        webTestClient.post().uri("/api/v1/wallets")
                .header("Content-Type", "application/json")
                .bodyValue("{\"ownerId\":\"00000000-0000-0000-0000-000000000001\",\"currency\":\"BRL\"}")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void transferWithoutTokenIsUnauthorized() {
        webTestClient.post().uri("/api/v1/transfers")
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", "sec-test-1")
                .bodyValue("{}")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void creditWithoutTokenIsUnauthorized() {
        webTestClient.post().uri("/api/v1/wallets/00000000-0000-0000-0000-000000000001/credits")
                .header("Content-Type", "application/json")
                .bodyValue("{}")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    /**
     * Mints a real HS256 token with the same secret the jwt-local decoder
     * uses, exercising the full decode + validate path instead of a
     * security-test mock.
     */
    private static String mintToken() throws Exception {
        JWSSigner signer = new MACSigner(LocalJwtSecrets.DEMO_SECRET);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("test-user")
                .issuer("local")
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .build();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.HS256).build(), claims);
        jwt.sign(signer);
        return jwt.serialize();
    }

    @Test
    void createWalletWithValidTokenPassesSecurity() throws Exception {
        // security passes; the request then fails at routing (no downstream in
        // test) — the assertion is that it is NOT 401/403 anymore
        webTestClient.post().uri("/api/v1/wallets")
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + mintToken())
                .bodyValue("{\"ownerId\":\"00000000-0000-0000-0000-000000000001\",\"currency\":\"BRL\"}")
                .exchange()
                .expectStatus().value(status -> org.assertj.core.api.Assertions.assertThat(status)
                        .isNotIn(401, 403));
    }
}
