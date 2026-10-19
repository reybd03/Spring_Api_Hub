package com.spring.api.hub.lifecycle;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DrainGateWebFilterTest {

    private final InFlightRequestTracker tracker = new InFlightRequestTracker();
    private final DrainGateWebFilter filter = new DrainGateWebFilter(tracker, "/actuator");

    private static Mono<Void> passThrough(ServerWebExchange exchange) {
        return Mono.empty();
    }

    private static Mono<Void> countingChain(ServerWebExchange exchange) {
        // Stands in for real work that stays in flight until the Mono completes.
        return Mono.empty();
    }

    @Test
    void countsBusinessRequestsForTheDurationOfTheCall() {
        ServerWebExchange exchange = MockServerWebExchange
                .from(MockServerHttpRequest.get("/products/automation"));

        StepVerifier.create(filter.filter(exchange, DrainGateWebFilterTest::countingChain)).verifyComplete();

        assertEquals(0, tracker.getInFlightCount(), "the counter must be released once the chain finishes");
    }

    @Test
    void countsARequestThatIsStillRunning() {
        ServerWebExchange exchange = MockServerWebExchange
                .from(MockServerHttpRequest.get("/products/automation"));
        Mono<Void> never = Mono.never();

        StepVerifier.create(filter.filter(exchange, ignored -> never))
                .expectSubscription()
                .then(() -> assertEquals(1, tracker.getInFlightCount()))
                .thenCancel()
                .verify();

        assertEquals(0, tracker.getInFlightCount(), "cancelling must release the counter too");
    }

    @Test
    void refusesNewBusinessRequestsOnceDraining() {
        tracker.beginDrain();
        ServerWebExchange exchange = MockServerWebExchange
                .from(MockServerHttpRequest.get("/products/automation"));

        StepVerifier.create(filter.filter(exchange, DrainGateWebFilterTest::failIfCalled))
                .verifyComplete();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exchange.getResponse().getStatusCode());
        assertEquals("5", exchange.getResponse().getHeaders().getFirst("Retry-After"));
        assertEquals(0, tracker.getInFlightCount(), "a refused request must not stay on the counter");
    }

    @Test
    void doesNotLoseARequestThatRacedTheDrain() {
        // The request is admitted, then the drain starts while it is still running. It must
        // remain counted so the drain waits for it rather than declaring victory early.
        ServerWebExchange exchange = MockServerWebExchange
                .from(MockServerHttpRequest.get("/products/automation"));

        StepVerifier.create(filter.filter(exchange, ignored -> Mono.never()))
                .expectSubscription()
                .then(tracker::beginDrain)
                .then(() -> assertEquals(1, tracker.getInFlightCount(),
                        "work admitted before the drain must still be waited for"))
                .thenCancel()
                .verify();

        assertEquals(0, tracker.getInFlightCount());
    }

    @Test
    void keepsManagementEndpointsReachableWhileDraining() {
        tracker.beginDrain();
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/actuator/health"));

        StepVerifier.create(filter.filter(exchange, DrainGateWebFilterTest::passThrough)).verifyComplete();

        assertEquals(null, exchange.getResponse().getStatusCode(),
                "health must stay answerable so the gateway can see the instance leave rotation");
    }

    @Test
    void keepsTheShutdownEndpointReachableWhileDraining() {
        tracker.beginDrain();
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/actuator/shutdown"));

        StepVerifier.create(filter.filter(exchange, DrainGateWebFilterTest::passThrough)).verifyComplete();

        assertEquals(null, exchange.getResponse().getStatusCode());
    }

    @Test
    void doesNotCountManagementTraffic() {
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/actuator/health"));

        StepVerifier.create(filter.filter(exchange, DrainGateWebFilterTest::passThrough)).verifyComplete();

        assertEquals(0, tracker.getInFlightCount());
    }

    @Test
    void doesNotTreatASimilarlyNamedPathAsManagement() {
        tracker.beginDrain();
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/actuatorish"));

        StepVerifier.create(filter.filter(exchange, DrainGateWebFilterTest::failIfCalled)).verifyComplete();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exchange.getResponse().getStatusCode());
    }

    @Test
    void honoursACustomManagementBasePath() {
        DrainGateWebFilter custom = new DrainGateWebFilter(tracker, "/manage");
        tracker.beginDrain();
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/manage/health"));

        StepVerifier.create(custom.filter(exchange, DrainGateWebFilterTest::passThrough)).verifyComplete();

        assertEquals(null, exchange.getResponse().getStatusCode());
    }

    private static Mono<Void> failIfCalled(ServerWebExchange exchange) {
        throw new AssertionError("the chain must not run for a refused request");
    }
}