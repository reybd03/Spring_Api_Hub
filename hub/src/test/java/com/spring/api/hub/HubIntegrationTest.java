package com.spring.api.hub;

import com.spring.api.hub.products.automation.AutomationEntity;
import com.spring.api.hub.products.automation.AutomationRepository;
import org.junit.jupiter.api.BeforeEach;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureWebTestClient
@Import(HubIntegrationTest.Containers.class)
class HubIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class Containers {
        @Bean
        @ServiceConnection
        MySQLContainer mysqlContainer() {
            return new MySQLContainer("mysql:8.4");
        }
    }

    @Autowired
    private AutomationRepository automationRepository;

    @Autowired
    private WebTestClient webTestClient;

    @BeforeEach
    void resetDatabase() {
        automationRepository.deleteAll();
    }

    @Test
    void listsPersistedAutomationProductsOverHttp() {
        AutomationEntity product = new AutomationEntity();
        product.setProductName("Integration Test Product");
        automationRepository.save(product);

        assertTrue(automationRepository.findByProductName("Integration Test Product").isPresent());

        webTestClient.get().uri("/products/automation")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].productName").isEqualTo("Integration Test Product")
                .jsonPath("$[1]").doesNotExist();
    }

    @Test
    void rendersHomePageOverHttp() {
        webTestClient.get().uri("/")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertTrue(body.contains("API Hub")));
    }
}
