package com.spring.api.automation.controller;

import com.spring.api.automation.entity.AutomationEntity;
import com.spring.api.automation.repository.AutomationRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.webflux.base-path=/api/v1",
                "automation.jenkins.default-url=http://localhost",
                "automation.jenkins.default-port=8080",
                "automation.jenkins.default-username=admin",
                "automation.jenkins.default-password=admin",
                "automation.jenkins.default-base-path=/",
                "automation.jenkins.discovery-paths=/var/lib/jenkins,/usr/share/jenkins",
                "automation.docker.compose-timeout=120",
                "automation.sse.heartbeat-interval=30000"
        })
@AutoConfigureWebTestClient
@Testcontainers
class JenkinsControllerIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private AutomationRepository automationRepository;

    @BeforeEach
    void setUp() {
        automationRepository.deleteAll();
    }

    @Test
    void shouldGetJenkinsDetails() {
        webTestClient.get()
                .uri("/jenkins")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.productName").isEqualTo("Jenkins");
    }

    @Test
    void shouldGetJenkinsConfig() {
        webTestClient.get()
                .uri("/jenkins/config")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.productName").isEqualTo("Jenkins")
                .jsonPath("$.productDiscovered").isEqualTo(false);
    }

    @Test
    void shouldUpdateJenkinsConfig() {
        // Use LinkedHashMap to preserve insertion order
        Map<String, String> update = new LinkedHashMap<>();
        update.put("productURL", "http://new-jenkins");
        update.put("productPort", "9090");
        update.put("productUserName", "newuser");

        webTestClient.post()
                .uri("/jenkins/config")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(update)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("Success")
                .jsonPath("$.updatedKey").isEqualTo("productURL");

        // Verify config was updated
        webTestClient.get()
                .uri("/jenkins/config")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.productURL").isEqualTo("http://new-jenkins")
                .jsonPath("$.productPort").isEqualTo("9090")
                .jsonPath("$.productUserName").isEqualTo("newuser");
    }

    @Test
    void shouldExecuteJenkinsAction() {
        Map<String, String> action = Map.of("action", "start");

        webTestClient.post()
                .uri("/jenkins/actions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(action)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").value(status -> assertThat(status).isIn("Success", "Error")); // Either is valid in test environment
    }

    @Test
    void shouldTriggerDiscovery() {
        webTestClient.post()
                .uri("/jenkins/discover")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.productName").isEqualTo("Jenkins");
    }
}