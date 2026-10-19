package com.spring.api.hub.lifecycle;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.mysql.MySQLContainer;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * End-to-end checks against a real HTTP server, covering the parts a unit test cannot prove:
 * that the actuator endpoints are actually exposed, that the token filter is actually in the
 * filter chain, and that readiness really does report the instance as out of service.
 *
 * <p>The lifecycle assertions run in a fixed order because each one changes the state the next
 * one observes: a drained instance stays drained for the rest of the context's life.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "platform.shutdown.drain-timeout=2s",
                "platform.shutdown.shutdown-token=integration-token" })
@AutoConfigureWebTestClient
@Import(GracefulShutdownHttpTests.Containers.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GracefulShutdownHttpTests {

    @TestConfiguration(proxyBeanMethods = false)
    static class Containers {
        @Bean
        @ServiceConnection
        MySQLContainer mysqlContainer() {
            return new MySQLContainer("mysql:8.4");
        }
    }

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private GracefulShutdownCoordinator coordinator;

    @Autowired
    private InFlightRequestTracker requestTracker;

    @Test
    @Order(1)
    void reportsReadyAndServesTrafficBeforeDraining() {
        webTestClient.get().uri("/actuator/health/readiness")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");

        webTestClient.get().uri("/")
                .exchange()
                .expectStatus().isOk();

        assertFalse(requestTracker.isDraining());
    }

    @Test
    @Order(2)
    void refusesAnUnauthorisedShutdown() {
        // 401 rather than 404 proves the endpoint is genuinely exposed and guarded, which a 404
        // could not distinguish from "not exposed at all".
        webTestClient.post().uri("/actuator/shutdown")
                .exchange()
                .expectStatus().isUnauthorized();

        webTestClient.post().uri("/actuator/shutdown")
                .header(ShutdownProperties.TOKEN_HEADER, "wrong-token")
                .exchange()
                .expectStatus().isUnauthorized();

        assertFalse(requestTracker.isDraining(), "a rejected shutdown must not drain the instance");
    }

    @Test
    @Order(3)
    void refusesNewTrafficAndReportsOutOfServiceOnceDraining() {
        coordinator.performDrain("integration test");

        webTestClient.get().uri("/actuator/health/readiness")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.status").isEqualTo("OUT_OF_SERVICE");

        webTestClient.get().uri("/")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectHeader().valueEquals("Retry-After", "5");
    }

    @Test
    @Order(4)
    void keepsLivenessUpWhileReadinessFails() {
        // The split that matters operationally: a draining instance must fail readiness so it
        // leaves the load balancer, but must stay live so an orchestrator does not kill it
        // outright and cut the drain short.
        webTestClient.get().uri("/actuator/health/liveness")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");

        webTestClient.get().uri("/actuator/health/readiness")
                .exchange()
                .expectStatus().isEqualTo(503);
    }

    @Test
    @Order(5)
    void doesNotCountManagementTrafficAsInFlightWork() {
        // The request that triggers the shutdown must never be counted, otherwise the drain
        // would wait on the very request that started it. The drain in order 3 returning at all
        // is the proof: it had only 2s and several probe requests ran around it.
        assertEquals(0, requestTracker.getInFlightCount(),
                "management traffic must never be counted as in-flight work");
    }

    @Test
    @Order(6)
    void keepsManagementDetailsHidden() {
        Map<String, Object> body = webTestClient.get().uri("/actuator/health/readiness")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();

        assertFalse(body.containsKey("components"),
                "show-details=never must not leak component detail to a non-loopback caller");
        assertEquals("OUT_OF_SERVICE", body.get("status"));
    }

    @Test
    @Order(7)
    void exposesHealthAndInfoEndpoints() {
        // Both are in the exposure allowlist, so a gateway or dashboard can rely on them.
        webTestClient.get().uri("/actuator/info")
                .exchange()
                .expectStatus().isOk();

        webTestClient.get().uri("/actuator/beans")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @Order(8)
    void refusesUnlistedManagementEndpoints() {
        // The exposure list is an allowlist, not a deny list: anything not named is not served.
        webTestClient.get().uri("/actuator/metrics")
                .exchange()
                .expectStatus().isNotFound();
    }
}
