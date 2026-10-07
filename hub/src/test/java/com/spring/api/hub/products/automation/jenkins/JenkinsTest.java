package com.spring.api.hub.products.automation.jenkins;

import com.spring.api.hub.products.automation.AutomationActions;
import com.spring.api.hub.products.automation.AutomationEntity;
import com.spring.api.hub.products.automation.AutomationRepository;
import com.spring.api.hub.products.automation.AutomationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class JenkinsTest {

    private AutomationRepository automationRepository;
    private AutomationService automationService;
    private AutomationActions automationActions;
    private Jenkins jenkins;

    @BeforeEach
    void setUp() {
        automationRepository = mock(AutomationRepository.class);
        automationService = mock(AutomationService.class);
        automationActions = mock(AutomationActions.class);
        WebClient.Builder webClientBuilder = mock(WebClient.Builder.class, RETURNS_DEEP_STUBS);

        jenkins = new Jenkins(automationRepository, automationService, webClientBuilder, automationActions);
    }

    @Test
    void testUnpackJenkinsMapWiresFieldsThroughAutomationService() {
        AutomationEntity entity = new AutomationEntity();
        entity.setProductName("Jenkins");
        when(automationRepository.findByProductName("Jenkins")).thenReturn(Optional.of(entity));

        jenkins.unpackJenkinsMap(Map.of("productURL", "http://jenkins.example.com"));
        verify(automationService).updateAutomationEntity(entity, "productURL", "http://jenkins.example.com");

        jenkins.unpackJenkinsMap(Map.of("productPort", "9090"));
        verify(automationService).updateAutomationEntity(entity, "productPort", "9090");

        jenkins.unpackJenkinsMap(Map.of("productUserName", "admin"));
        verify(automationService).updateAutomationEntity(entity, "productUserName", "admin");

        jenkins.unpackJenkinsMap(Map.of("productPassword", "secret"));
        verify(automationService).updateAutomationEntity(entity, "productPassword", "secret");

        jenkins.unpackJenkinsMap(Map.of("productAPIKey", "token123"));
        verify(automationService).updateAutomationEntity(entity, "productAPIKey", "token123");
    }
}
