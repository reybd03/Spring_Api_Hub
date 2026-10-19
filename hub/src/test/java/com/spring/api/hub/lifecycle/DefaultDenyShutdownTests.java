package com.spring.api.hub.lifecycle;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.mysql.MySQLContainer;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Verifies the shipped default is deny: with no {@code platform.shutdown.shutdown-token}
 * provisioned, the kill switch must not be usable over HTTP.
 *
 * <p>This deliberately omits the token so it exercises the out-of-the-box posture that every
 * deployment starts in. The companion test in {@code GracefulShutdownHttpTests} covers the
 * configured case.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = { "spring.jpa.hibernate.ddl-auto=create-drop" })
@AutoConfigureWebTestClient
@Import(DefaultDenyShutdownTests.Containers.class)
class DefaultDenyShutdownTests {

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
    private ShutdownProperties properties;

    @Test
    void shipsWithNoShutdownSecret() {
        assertFalse(properties.hasToken(),
                "no secret should be provisioned by default; supply HUB_SHUTDOWN_TOKEN to enable it");
    }

    @Test
    void answersNotFoundForShutdownWhenNoSecretIsProvisioned() {
        // 404 rather than 401 or 200: with no secret the endpoint must be indistinguishable
        // from one that was never exposed, so it cannot be probed for.
        webTestClient.post().uri("/actuator/shutdown")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void refusesShutdownEvenWhenATokenGuessIsSupplied() {
        webTestClient.post().uri("/actuator/shutdown")
                .header(ShutdownProperties.TOKEN_HEADER, "guessed")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void keepsHealthEndpointsAvailableWithNoSecretConfigured() {
        // Failing closed on shutdown must not take the probe endpoints down with it, or a
        // gateway would have no way to observe the instance at all.
        webTestClient.get().uri("/actuator/health/readiness")
                .exchange()
                .expectStatus().isOk();
    }
}