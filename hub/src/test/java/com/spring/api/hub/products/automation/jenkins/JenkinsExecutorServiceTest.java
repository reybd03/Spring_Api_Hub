package com.spring.api.hub.products.automation.jenkins;

import com.spring.api.hub.WebController;
import com.spring.api.hub.products.automation.AutomationActions;
import com.spring.api.hub.products.automation.AutomationEntity;
import com.spring.api.hub.products.automation.AutomationRepository;
import com.spring.api.hub.products.automation.AutomationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class JenkinsExecutorServiceTest {

    private WebController webController;
    private AutomationRepository automationRepository;
    private Jenkins jenkins;
    private AutomationActions automationActions;
    private AutomationService automationService;
    private JenkinsExecutorService executorService;
    private AutomationEntity entity;

    @BeforeEach
    void setUp() {
        webController = mock(WebController.class);
        automationRepository = mock(AutomationRepository.class);
        jenkins = mock(Jenkins.class);
        automationActions = mock(AutomationActions.class);
        automationService = mock(AutomationService.class);

        entity = new AutomationEntity();
        entity.setProductName("Jenkins");
        entity.setProductBasePath("/tmp/jenkins");
        entity.setProductStatus("Stopped");

        when(jenkins.fetchJenkinsEntity()).thenReturn(entity);
        when(automationRepository.save(any(AutomationEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        executorService = new JenkinsExecutorService(
                webController,
                automationRepository,
                jenkins,
                automationActions,
                automationService
        );
    }

    @Test
    void testStartSucceedsWhenComposeRunning() {
        when(automationActions.executeProductProcess(anyList(), anyString(), anyString())).thenReturn(1234L);
        when(automationActions.isComposeRunning("/tmp/jenkins")).thenReturn(true);

        String result = executorService.actionGateway("Jenkins", "start");

        assertEquals("Success", result);
        assertEquals("Running", entity.getProductStatus());
        assertEquals(1234L, entity.getProductProcessId());
        verify(automationRepository).save(entity);
    }

    @Test
    void testStartFailsWhenComposeFails() {
        when(automationActions.executeProductProcess(anyList(), anyString(), anyString())).thenReturn(-1L);
        when(automationActions.isComposeRunning("/tmp/jenkins")).thenReturn(false);
        when(automationActions.isPidRunning(-1L)).thenReturn(false);

        String result = executorService.actionGateway("Jenkins", "start");

        assertEquals("Success", result);
        assertEquals("Failed", entity.getProductStatus());
        verify(automationRepository).save(entity);
    }

    @Test
    void testStopSucceedsWhenComposeStopped() {
        entity.setProductStatus("Running");
        entity.setProductProcessId(1234L);

        when(automationActions.executeProductProcess(anyList(), anyString(), anyString())).thenReturn(5678L);
        when(automationActions.isComposeRunning("/tmp/jenkins")).thenReturn(false);
        when(automationActions.isPidRunning(1234L)).thenReturn(false);

        String result = executorService.actionGateway("Jenkins", "stop");

        assertEquals("Success", result);
        assertEquals("Stopped", entity.getProductStatus());
        verify(automationRepository).save(entity);
    }
}
