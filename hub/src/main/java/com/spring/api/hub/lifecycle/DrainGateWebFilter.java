package com.spring.api.hub.lifecycle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

/**
 * Turns {@link InFlightRequestTracker#beginDrain()} into behaviour: while the instance is
 * draining, new business requests get {@code 503} instead of being served against resources
 * that are about to disappear.
 *
 * <p>Management endpoints are exempt in both directions. They are neither counted nor refused,
 * which is deliberate: the shutdown request that triggers this whole sequence would otherwise
 * be waiting on itself, and the health endpoints have to stay answerable so the gateway can see
 * the instance leave rotation.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DrainGateWebFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(DrainGateWebFilter.class);
    private static final String RETRY_AFTER_SECONDS = "5";

    private final InFlightRequestTracker requestTracker;
    private final String managementPathPrefix;

    public DrainGateWebFilter(InFlightRequestTracker requestTracker,
            @Value("${management.endpoints.web.base-path:/actuator}") String managementPathPrefix) {
        this.requestTracker = requestTracker;
        this.managementPathPrefix = ActuatorPaths.basePath(managementPathPrefix);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (isManagementPath(exchange)) {
            return chain.filter(exchange);
        }

        // Count before testing for the drain, not after. A request admitted just before the drain
        // starts is still real work that has to be waited for, so it must be visible to the
        // quiescence check even though it will be refused a moment later.
        requestTracker.beginRequest();
        if (requestTracker.isDraining()) {
            requestTracker.endRequest();
            return reject(exchange);
        }

        // doFinally rather than a success callback, so cancelled clients
        // (an SSE consumer navigating away) release the counter too.
        return chain.filter(exchange).doFinally(signal -> requestTracker.endRequest());
    }

    private Mono<Void> reject(ServerWebExchange exchange) {
        log.debug("Refusing {} {} while draining", exchange.getRequest().getMethod(),
                exchange.getRequest().getPath().pathWithinApplication().value());
        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        exchange.getResponse().getHeaders().set(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS);
        return exchange.getResponse().setComplete();
    }

    private boolean isManagementPath(ServerWebExchange exchange) {
        return ActuatorPaths.isManagementPath(exchange.getRequest().getPath().pathWithinApplication().value(),
                managementPathPrefix);
    }
}