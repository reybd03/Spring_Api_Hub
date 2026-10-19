package com.spring.api.hub.lifecycle;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

/**
 * Guards {@code POST /actuator/shutdown} with a shared secret.
 *
 * <p>Spring Boot disables this endpoint by default and it stays disabled unless
 * {@code management.endpoint.shutdown.access=unrestricted} is set, which is what the platform
 * configuration does. Exposing an unauthenticated kill switch on a service that holds a database
 * connection and drives host processes is not an acceptable default, so this filter fails
 * closed: with no token provisioned the endpoint answers {@code 404} to everyone, and with a
 * token provisioned a wrong or missing one answers {@code 401}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class ShutdownTokenWebFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(ShutdownTokenWebFilter.class);

    private final ShutdownProperties properties;
    private final String shutdownPath;

    public ShutdownTokenWebFilter(ShutdownProperties properties,
            @Value("${management.endpoints.web.base-path:/actuator}") String managementPathPrefix) {
        this.properties = properties;
        this.shutdownPath = ActuatorPaths.basePath(managementPathPrefix) + "/shutdown";
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!isShutdownEndpoint(exchange)) {
            return chain.filter(exchange);
        }

        if (!properties.hasToken()) {
            // Answer 404 rather than 403: without a provisioned secret the endpoint should look
            // absent, not merely locked, so this cannot be used to probe for its existence.
            log.warn("Refused {} because platform.shutdown.shutdown-token is not configured",
                    exchange.getRequest().getPath().pathWithinApplication().value());
            exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
            return exchange.getResponse().setComplete();
        }

        String presented = exchange.getRequest().getHeaders().getFirst(ShutdownProperties.TOKEN_HEADER);
        if (!matches(presented, properties.shutdownToken())) {
            log.warn("Rejected unauthenticated shutdown attempt from {}", exchange.getRequest().getRemoteAddress());
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        log.info("Shutdown endpoint authorised by {}", exchange.getRequest().getRemoteAddress());
        return chain.filter(exchange);
    }

    private boolean isShutdownEndpoint(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        return path.equals(shutdownPath);
    }

    /**
     * Constant-time comparison so a caller cannot learn the secret by measuring response times.
     */
    private static boolean matches(String presented, String expected) {
        if (presented == null) {
            return false;
        }
        return MessageDigest.isEqual(presented.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
    }
}