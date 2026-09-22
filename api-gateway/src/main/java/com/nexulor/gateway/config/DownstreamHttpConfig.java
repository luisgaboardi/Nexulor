package com.nexulor.gateway.config;

import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/**
 * Non-blocking WebClient used by the GraphQL aggregation layer to reach the
 * downstream REST services. Connection and response timeouts come from
 * {@link GatewayProperties.Downstream}.
 */
@Configuration
class DownstreamHttpConfig {

    @Bean
    WebClient downstreamWebClient(GatewayProperties properties) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) properties.downstream().timeout().toMillis())
                .responseTimeout(properties.downstream().timeout());
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
