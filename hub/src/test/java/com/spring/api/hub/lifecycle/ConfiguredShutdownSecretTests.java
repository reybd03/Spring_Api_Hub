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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the shutdown endpoint really does shut the instance down once a secret is
 * provisioned.
 *
 * <p>This is the only test that closes its own application context, so it holds exactly one
 * assertion and lets the shutdown proceed. The other HTTP tests deliberately stop short of
 * triggering shutdown, since a context that closes takes its remaining assertions with it.
 *
 * <p>Drain idempotency is covered by {@code GracefulShutdownCoordinatorTest}, where it can be
 * exercised without destroying the context the assertions need.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "platform.shutdown.drain-timeout=2s",
                "platform.shutdown.shutdown-token=integration-token",
                "logging.level.com.spring.api.hub.lifecycle=INFO" })
@AutoConfigureWebTestClient
@Import(ConfiguredShutdownSecretTests.Containers.class)
class ConfiguredShutdownSecretTests {

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
    private InFlightRequestTracker requestTracker;

    @Test
    void shutsTheInstanceDownWhenAuthorised() {
        // Confirm the instance is serving first, so the drain below is proven to have caused the
        // refusal rather than the server having been broken to begin with.
        webTestClient.get().uri("/")
                .exchange()
                .expectStatus().isOk();

        webTestClient.post().uri("/actuator/shutdown")
                .header(ShutdownProperties.TOKEN_HEADER, "integration-token")
                .exchange()
                .expectStatus().isOk();

        // The endpoint responds before the context has finished closing, so wait for the drain
        // to actually take effect instead of racing it.
        awaitDrain();

        assertTrue(requestTracker.isDraining(),
                "an authorised shutdown must drain the instance rather than closing it abruptly");
    }

    private void awaitDrain() {
        long deadline = System.currentTimeMillis() + 5_000;
        while (!requestTracker.isDraining() && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(25);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}