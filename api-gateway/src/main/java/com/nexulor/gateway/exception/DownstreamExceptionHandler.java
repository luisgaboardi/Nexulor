package com.nexulor.gateway.exception;

import graphql.GraphQLError;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.graphql.execution.DataFetcherExceptionResolver;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Maps downstream failures to GraphQL errors: an unknown wallet becomes
 * NOT_FOUND; an unreachable service after retries becomes INTERNAL_ERROR with
 * a machine-readable {@code code} extension (WALLET_UNAVAILABLE /
 * STATEMENT_UNAVAILABLE) so clients can react to availability, not just
 * transport status.
 */
@Component
public class DownstreamExceptionHandler implements DataFetcherExceptionResolver {

    @Override
    public Mono<List<GraphQLError>> resolveException(Throwable ex, DataFetchingEnvironment environment) {
        if (ex instanceof DownstreamCallException e) {
            ErrorType type = "WALLET_NOT_FOUND".equals(e.code())
                    ? ErrorType.NOT_FOUND
                    : ErrorType.INTERNAL_ERROR;
            GraphQLError error = GraphQLError.newError()
                    .errorType(type)
                    .message(e.getMessage())
                    .extensions(Map.of("code", e.code()))
                    .build();
            return Mono.just(List.of(error));
        }
        return Mono.empty();
    }
}
