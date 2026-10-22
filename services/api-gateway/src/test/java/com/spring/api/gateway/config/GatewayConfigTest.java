package com.spring.api.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.redis.host=localhost",
        "spring.redis.port=6379"
})
class GatewayConfigTest {

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void routeLocatorShouldBeConfigured() {
        assertThat(routeLocator).isNotNull();
    }
}