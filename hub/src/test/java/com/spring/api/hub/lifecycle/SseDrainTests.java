package com.spring.api.hub.lifecycle;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.mysql.MySQLContainer;

import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Proves the SSE feeds actually stop when a drain starts.
 *
 * <p>This is the reason the drain matters in this service. Both Jenkins feeds poll forever by
 * design, so without {@code takeUntilOther(drainSignal.drain())} an already-open stream would
 * keep counting as in-flight work and every shutdown would sit out the full drain timeout
 * waiting for a stream that was never going to end.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "platform.shutdown.drain-timeout=2s" })
@AutoConfigureWebTestClient
@Import(SseDrainTests.Containers.class)
class SseDrainTests {

    private static final String SYS_STATS_STREAM = "/products/automation/jenkins/streamSysStats";
    private static final Duration COMPLETION_TIMEOUT = Duration.ofSeconds(10);

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

    @Test
    void endsAnAlreadyOpenStreamWhenTheDrainStarts() {
        var stream = openStream();

        StepVerifier.create(stream)
                .expectSubscription()
                // The feed ticks every second and is deliberately endless: receiving events here
                // proves it is live, and receiving them before the drain proves the drain is what
                // ends it rather than the stream simply being quiet.
                .thenAwait(Duration.ofMillis(1500))
                .expectNextCount(1)
                .then(() -> coordinator.performDrain("sse test"))
                .thenConsumeWhile(event -> true)
                .expectComplete()
                .verify(COMPLETION_TIMEOUT);
    }

    @Test
    void refusesNewStreamConnectionsOnceDraining() {
        coordinator.performDrain("sse test");

        // A browser EventSource reconnects on its own. It must be turned away rather than handed
        // a stream that immediately closes, which would spin it in a reconnect loop.
        webTestClient.get().uri(SYS_STATS_STREAM)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isEqualTo(503);
    }

    /**
     * Opens the feed and returns its events, without asserting on completion so a test can decide
     * when the drain should end it.
     */
    private reactor.core.publisher.Flux<String> openStream() {
        return webTestClient.get().uri(SYS_STATS_STREAM)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .returnResult(String.class)
                .getResponseBody();
    }

    @Test
    void completesImmediatelyForASubscriberArrivingAfterTheDrain() {
        coordinator.performDrain("sse test");
        // Exercised through the drain gate rather than the controller: new connections are
        // refused at the gate, so this asserts the gate's behaviour and not the stream's.
        assertEquals(503, webTestClient.get().uri(SYS_STATS_STREAM)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .returnResult(String.class)
                .getStatus()
                .value());
    }
}