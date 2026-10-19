package com.spring.api.hub.lifecycle;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ShutdownTokenWebFilterTest {

    private static final String SECRET = "s3cr3t-shutdown-token";

    private static Mono<Void> passThrough(ServerWebExchange exchange) {
        return Mono.empty();
    }

    private static ShutdownTokenWebFilter filterWith(String token) {
        return new ShutdownTokenWebFilter(new ShutdownProperties(null, token), "/actuator");
    }

    private static MockServerWebExchange shutdownRequest(String tokenHeader) {
        MockServerHttpRequest.BaseBuilder<?> request = MockServerHttpRequest.post("/actuator/shutdown");
        if (tokenHeader != null) {
            request = request.header(ShutdownProperties.TOKEN_HEADER, tokenHeader);
        }
        return MockServerWebExchange.from(request);
    }

    @Test
    void admitsAnAuthorisedShutdown() {
        ShutdownTokenWebFilter filter = filterWith(SECRET);
        MockServerWebExchange exchange = shutdownRequest(SECRET);

        StepVerifier.create(filter.filter(exchange, ShutdownTokenWebFilterTest::passThrough)).verifyComplete();

        assertNull(exchange.getResponse().getStatusCode(), "an authorised shutdown must reach the endpoint");
    }

    @Test
    void rejectsAWrongToken() {
        ShutdownTokenWebFilter filter = filterWith(SECRET);
        MockServerWebExchange exchange = shutdownRequest("not-the-secret");

        StepVerifier.create(filter.filter(exchange, ShutdownTokenWebFilterTest::failIfCalled)).verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void rejectsAMissingToken() {
        ShutdownTokenWebFilter filter = filterWith(SECRET);
        MockServerWebExchange exchange = shutdownRequest(null);

        StepVerifier.create(filter.filter(exchange, ShutdownTokenWebFilterTest::failIfCalled)).verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void rejectsATokenThatOnlySharesAPrefix() {
        ShutdownTokenWebFilter filter = filterWith(SECRET);
        MockServerWebExchange exchange = shutdownRequest(SECRET.substring(0, SECRET.length() - 1));

        StepVerifier.create(filter.filter(exchange, ShutdownTokenWebFilterTest::failIfCalled)).verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void hidesTheEndpointEntirelyWhenNoSecretIsProvisioned() {
        ShutdownTokenWebFilter filter = filterWith("");
        MockServerWebExchange exchange = shutdownRequest("anything");

        StepVerifier.create(filter.filter(exchange, ShutdownTokenWebFilterTest::failIfCalled)).verifyComplete();

        assertEquals(HttpStatus.NOT_FOUND, exchange.getResponse().getStatusCode(),
                "with no secret the endpoint must look absent, not merely locked");
    }

    @Test
    void refusesEvenTheCorrectTokenWhenNoSecretIsProvisioned() {
        ShutdownTokenWebFilter filter = filterWith("   ");
        MockServerWebExchange exchange = shutdownRequest(SECRET);

        StepVerifier.create(filter.filter(exchange, ShutdownTokenWebFilterTest::failIfCalled)).verifyComplete();

        assertEquals(HttpStatus.NOT_FOUND, exchange.getResponse().getStatusCode());
    }

    @Test
    void ignoresEveryOtherManagementEndpoint() {
        ShutdownTokenWebFilter filter = filterWith("");
        MockServerWebExchange health = MockServerWebExchange.from(MockServerHttpRequest.get("/actuator/health"));
        MockServerWebExchange readiness = MockServerWebExchange.from(MockServerHttpRequest.get("/actuator/health/readiness"));

        StepVerifier.create(filter.filter(health, ShutdownTokenWebFilterTest::passThrough)).verifyComplete();
        StepVerifier.create(filter.filter(readiness, ShutdownTokenWebFilterTest::passThrough)).verifyComplete();

        assertNull(health.getResponse().getStatusCode());
        assertNull(readiness.getResponse().getStatusCode());
    }

    @Test
    void ignoresBusinessTraffic() {
        ShutdownTokenWebFilter filter = filterWith("");
        MockServerWebExchange exchange = MockServerWebExchange
                .from(MockServerHttpRequest.post("/products/automation/jenkins/actions"));

        StepVerifier.create(filter.filter(exchange, ShutdownTokenWebFilterTest::passThrough)).verifyComplete();

        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    void doesNotConfuseShutdownWithASimilarlyNamedEndpoint() {
        ShutdownTokenWebFilter filter = filterWith(SECRET);
        MockServerWebExchange exchange = MockServerWebExchange
                .from(MockServerHttpRequest.get("/actuator/shutdown/status"));

        StepVerifier.create(filter.filter(exchange, ShutdownTokenWebFilterTest::passThrough)).verifyComplete();

        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    void followsACustomManagementBasePath() {
        ShutdownTokenWebFilter filter = new ShutdownTokenWebFilter(new ShutdownProperties(null, SECRET), "/manage");
        MockServerWebExchange exchange = MockServerWebExchange
                .from(MockServerHttpRequest.post("/manage/shutdown").header(ShutdownProperties.TOKEN_HEADER, SECRET));

        StepVerifier.create(filter.filter(exchange, ShutdownTokenWebFilterTest::passThrough)).verifyComplete();

        assertNull(exchange.getResponse().getStatusCode());
    }

    private static Mono<Void> failIfCalled(ServerWebExchange exchange) {
        throw new AssertionError("the chain must not run for a rejected shutdown");
    }
}