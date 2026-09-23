package com.nexulor.gateway.config;

import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Local/dev decoder ({@code jwt-local} profile): HS256 tokens signed with a
 * shared secret, minted with the bash helper documented in the README. This
 * keeps the showcase runnable without a full authorization server.
 *
 * <p>In production, drop this profile and set
 * {@code spring.security.oauth2.resourceserver.jwt.issuer-uri}: Boot auto-
 * configures a JWKS-based RS256 decoder and only this bean disappears — the
 * {@link SecurityConfig} rules stay unchanged.</p>
 */
@Configuration
@Profile("jwt-local")
class LocalJwtDecoderConfig {

    @Bean
    ReactiveJwtDecoder jwtDecoder(OAuth2ResourceServerProperties properties) {
        String secret = System.getProperty(
                "gateway.jwt.local-secret",
                System.getenv().getOrDefault("JWT_LOCAL_SECRET", LocalJwtSecrets.DEMO_SECRET));
        SecretKeySpec key = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefault());
        return decoder;
    }
}
