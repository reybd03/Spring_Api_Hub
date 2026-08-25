package com.spring.api.hub;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {

    /*
     * builder.routes(): Build routes.
     * builder.route(): Creates a route.
     * .route(routeId, predicate, handler):
     * - routeId: Unique identifier for the route.
     * - predicate: Condition that must be met for the route to be applied.
     * - handler: Action to take when the route is applied.
     */
    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("path_route", r -> r.path("/products/**")
                        .uri("http://localhost:8082"))
                .build();
    }
}
