package com.spring.api.gateway.config;

import org.springframework.cloud.client.loadbalancer.reactive.ReactorLoadBalancerExchangeFilterFunction;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.function.Function;

@Configuration
public class GatewayConfig {

    /**
     * Custom RouteLocator for programmatic route configuration.
     * Routes are primarily defined in application.yml, but this bean allows
     * for dynamic route registration if needed.
     */
    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                // Routes are configured via application.yml
                // This bean exists for any dynamic route registration needs
                .build();
    }

    /**
     * Key resolver for rate limiting based on client IP address.
     * Uses X-Forwarded-For header if present, otherwise falls back to remote address.
     */
    @Bean
    public Function<org.springframework.web.server.ServerWebExchange, Mono<String>> ipKeyResolver() {
        return exchange -> Mono.just(exchange.getRequest().getHeaders().getFirst("X-Forwarded-For"))
                .filter(ip -> ip != null && !ip.isEmpty())
                .map(ip -> ip.split(",")[0].trim())
                .switchIfEmpty(Mono.defer(() -> Mono.justOrEmpty(exchange.getRequest().getRemoteAddress())
                        .map(addr -> addr.getAddress().getHostAddress())))
                .defaultIfEmpty("unknown");
    }

    /**
     * Load-balanced WebClient for downstream service communication.
     * Used by filters that need to make HTTP calls to other services.
     */
    @Bean
    public WebClient loadBalancedWebClient(ReactorLoadBalancerExchangeFilterFunction lbFunction) {
        return WebClient.builder()
                .filter(lbFunction)
                .build();
    }
}