package com.spring.api.automation;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.MySQLContainer;

@SpringBootTest(properties = {
    "server.port=0",
    "automation.jenkins.default-url=http://localhost",
    "automation.jenkins.default-port=8080",
    "automation.jenkins.default-username=admin",
    "automation.jenkins.default-password=admin",
    "automation.jenkins.default-base-path=/",
    "automation.jenkins.discovery-paths=/var/lib/jenkins,/usr/share/jenkins",
    "automation.docker.compose-timeout=120",
    "automation.sse.heartbeat-interval=30000"
})
@Import(AutomationServiceApplicationTests.Containers.class)
class AutomationServiceApplicationTests {

    @TestConfiguration(proxyBeanMethods = false)
    static class Containers {
        @Bean
        @ServiceConnection
        MySQLContainer<?> mysqlContainer() {
            return new MySQLContainer<>("mysql:8.4");
        }
    }

    @Test
    void contextLoads() {
    }
}