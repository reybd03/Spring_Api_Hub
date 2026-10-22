package com.spring.api.gateway.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class FallbackControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void automationFallbackShouldReturn503WithErrorDetails() {
        webTestClient.get()
                .uri("/fallback/automation")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.status").isEqualTo(503)
                .jsonPath("$.error").isEqualTo("Service Unavailable")
                .jsonPath("$.message").value(msg -> assertThat(msg.toString()).contains("Automation service"))
                .jsonPath("$.timestamp").exists();
    }

    @Test
    void defaultFallbackShouldReturn503WithErrorDetails() {
        webTestClient.get()
                .uri("/fallback/default")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.status").isEqualTo(503)
                .jsonPath("$.error").isEqualTo("Service Unavailable")
                .jsonPath("$.message").value(msg -> assertThat(msg.toString()).contains("requested service"))
                .jsonPath("$.timestamp").exists();
    }
}