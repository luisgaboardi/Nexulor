package com.nexulor.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Security policies at the single entry point (PRD 2.1): the gateway is the
 * OAuth2 resource server validating JWTs; downstream services stay on the
 * internal network and trust the gateway.
 *
 * <p>Unauthenticated clients keep read-only access to the showcase endpoints
 * (health, GraphiQL, queries); every financial mutation (wallet creation,
 * credits, transfers) requires a valid JWT.</p>
 */
@Configuration
@EnableWebFluxSecurity
class SecurityConfig {

    private static final String[] PUBLIC_GET_PATHS = {
            "/actuator/**",
            "/graphiql",
            "/graphql/**"
    };

    @Bean
    SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.GET, PUBLIC_GET_PATHS).permitAll()
                        .pathMatchers(HttpMethod.POST, "/graphql").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/v1/wallets").authenticated()
                        .pathMatchers(HttpMethod.POST, "/api/v1/wallets/*/credits").authenticated()
                        .pathMatchers(HttpMethod.POST, "/api/v1/transfers").authenticated()
                        .anyExchange().permitAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        // withDefaults() resolves the ReactiveJwtDecoder bean from
                        // the context: LocalJwtDecoderConfig (HS256, jwt-local) or
                        // Boot's JWKS auto-config (issuer-uri, production)
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint((exchange, ex) -> {
                            var response = exchange.getResponse();
                            response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
                            response.getHeaders().add("WWW-Authenticate", "Bearer");
                            return response.setComplete();
                        }))
                .build();
    }
}
