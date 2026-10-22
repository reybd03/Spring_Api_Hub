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

import java.util.List;

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
class AutomationControllerIntegrationTest {

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
    void shouldCreateAndGetAutomationEntity() {
        AutomationEntity entity = new AutomationEntity();
        entity.setProductName("TestProduct");
        entity.setProductDiscoveryStatus("Completed");
        entity.setProductDiscovered(true);
        entity.setProductBasePath("/test");
        entity.setProductURL("http://test");
        entity.setProductPort("8080");

        // Create - try with relative path
        webTestClient.post()
                .uri("/automation")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(entity)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.productName").isEqualTo("TestProduct")
                .jsonPath("$.id").exists();

        // Get all - try with relative path
        webTestClient.get()
                .uri("/automation")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(AutomationEntity.class)
                .hasSize(1)
                .value(list -> assertThat(list.get(0).getProductName()).isEqualTo("TestProduct"));
    }

    @Test
    void shouldReturn404ForNonExistentEntity() {
        webTestClient.get()
                .uri("/automation/999")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void shouldUpdateAutomationEntity() {
        // Create first
        AutomationEntity entity = new AutomationEntity();
        entity.setProductName("UpdateTest");
        entity.setProductDiscoveryStatus("Pending");
        entity.setProductDiscovered(false);

        var createResponse = webTestClient.post()
                .uri("/automation")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(entity)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(AutomationEntity.class)
                .returnResult()
                .getResponseBody();

        Long id = createResponse.getId();

        // Update
        AutomationEntity update = new AutomationEntity();
        update.setProductName("UpdatedProduct");
        update.setProductDiscoveryStatus("Completed");
        update.setProductDiscovered(true);

        webTestClient.put()
                .uri("/automation/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(update)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.productName").isEqualTo("UpdatedProduct")
                .jsonPath("$.productDiscovered").isEqualTo(true);
    }

    @Test
    void shouldDeleteAutomationEntity() {
        AutomationEntity entity = new AutomationEntity();
        entity.setProductName("DeleteTest");

        var createResponse = webTestClient.post()
                .uri("/automation")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(entity)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(AutomationEntity.class)
                .returnResult()
                .getResponseBody();

        Long id = createResponse.getId();

        // Delete
        webTestClient.delete()
                .uri("/automation/{id}", id)
                .exchange()
                .expectStatus().isNoContent();

        // Verify deleted
        webTestClient.get()
                .uri("/automation/{id}", id)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void shouldReturnBadRequestForDuplicateProductName() {
        AutomationEntity entity = new AutomationEntity();
        entity.setProductName("DuplicateTest");

        webTestClient.post()
                .uri("/automation")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(entity)
                .exchange()
                .expectStatus().isCreated();

        webTestClient.post()
                .uri("/automation")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(entity)
                .exchange()
                .expectStatus().isBadRequest();
    }
}