package com.spring.api.hub.controllers;

import com.spring.api.hub.products.automation.AutomationEntity;
import com.spring.api.hub.products.automation.AutomationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class ProductAutomationControllerTest {

    private AutomationService automationService;
    private ProductAutomationController controller;

    @BeforeEach
    void setUp() {
        automationService = mock(AutomationService.class);
        controller = new ProductAutomationController(automationService);
    }

    @Test
    void testGetAllAutomationEntities() {
        AutomationEntity entity1 = new AutomationEntity();
        entity1.setProductName("Jenkins");
        AutomationEntity entity2 = new AutomationEntity();
        entity2.setProductName("Ansible");

        when(automationService.findAllAutomationEntitiesMono()).thenReturn(Mono.just(List.of(entity1, entity2)));

        StepVerifier.create(controller.getAllAutomationEntities())
                .assertNext(e -> assertEquals("Jenkins", e.getProductName()))
                .assertNext(e -> assertEquals("Ansible", e.getProductName()))
                .verifyComplete();

        verify(automationService).findAllAutomationEntitiesMono();
    }
}
