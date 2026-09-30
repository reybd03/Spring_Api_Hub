package com.spring.api.hub.products.automation.jenkins;

import com.spring.api.hub.WebController;
import com.spring.api.hub.products.automation.AutomationEntity;
import com.spring.api.hub.products.automation.AutomationRepository;
import com.spring.api.hub.products.automation.AutomationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class JenkinsControllerTest {

    private WebController webController;
    private Jenkins jenkins;
    private JenkinsExecutorService jenkinsExecutorService;
    private AutomationRepository automationRepository;
    private AutomationService automationService;
    private JenkinsController jenkinsController;

    @BeforeEach
    void setUp() {
        webController = mock(WebController.class);
        jenkins = mock(Jenkins.class);
        jenkinsExecutorService = mock(JenkinsExecutorService.class);
        automationRepository = mock(AutomationRepository.class);
        automationService = mock(AutomationService.class);

        jenkinsController = new JenkinsController(
                webController,
                jenkins,
                automationRepository,
                automationService,
                jenkinsExecutorService
        );
    }

    @Test
    void testJenkinsPageRendersOffloaded() {
        AutomationEntity entity = new AutomationEntity();
        entity.setProductName("Jenkins");
        when(jenkins.getJenkinsDetails()).thenReturn(entity);

        Model model = new ConcurrentModel();
        StepVerifier.create(jenkinsController.JenkinsPage(model))
                .expectNext("pages")
                .verifyComplete();

        assertEquals("Jenkins", model.getAttribute("title"));
        assertEquals(entity, model.getAttribute("jenkinsConfig"));
        verify(jenkins).getJenkinsDetails();
    }

    @Test
    void testUpdateJenkinsProductOffloaded() {
        when(jenkins.unpackJenkinsMap(any())).thenReturn("productBasePath");

        StepVerifier.create(jenkinsController.updateJenkinsProduct(Map.of("productBasePath", "/var/jenkins")))
                .assertNext(res -> {
                    assertEquals("Success", res.get("status"));
                    assertTrue(res.get("message").contains("productBasePath"));
                })
                .verifyComplete();
    }

    @Test
    void testJenkinsProductActionsOffloaded() {
        when(jenkinsExecutorService.actionGateway("Jenkins", "start")).thenReturn("Success");

        StepVerifier.create(jenkinsController.jenkinsProductActions(Map.of("target", "Jenkins", "action", "start")))
                .assertNext(res -> {
                    assertEquals("Success", res.get("status"));
                })
                .verifyComplete();
    }
}
