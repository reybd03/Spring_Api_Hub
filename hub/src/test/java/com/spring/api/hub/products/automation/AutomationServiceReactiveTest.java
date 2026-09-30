package com.spring.api.hub.products.automation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AutomationServiceReactiveTest {

    private AutomationRepository automationRepository;
    private AutomationService automationService;

    @BeforeEach
    void setUp() {
        automationRepository = mock(AutomationRepository.class);
        automationService = new AutomationService(automationRepository);
    }

    @Test
    void testSaveAutomationEntityMono() {
        AutomationEntity entity = new AutomationEntity();
        entity.setProductName("Jenkins");
        when(automationRepository.save(any(AutomationEntity.class))).thenReturn(entity);

        Mono<AutomationEntity> result = automationService.saveAutomationEntityMono(entity);

        StepVerifier.create(result)
                .assertNext(saved -> assertEquals("Jenkins", saved.getProductName()))
                .verifyComplete();

        verify(automationRepository).save(entity);
    }

    @Test
    void testFindByProductNameMono() {
        AutomationEntity entity = new AutomationEntity();
        entity.setProductName("Jenkins");
        when(automationRepository.findByProductName("Jenkins")).thenReturn(Optional.of(entity));

        Mono<Optional<AutomationEntity>> result = automationService.findByProductNameMono("Jenkins");

        StepVerifier.create(result)
                .assertNext(opt -> {
                    assertNotNull(opt.orElse(null));
                    assertEquals("Jenkins", opt.get().getProductName());
                })
                .verifyComplete();
    }

    @Test
    void testFindAllAutomationEntitiesMono() {
        AutomationEntity entity = new AutomationEntity();
        entity.setProductName("Jenkins");
        when(automationRepository.findAll()).thenReturn(List.of(entity));

        Mono<List<AutomationEntity>> result = automationService.findAllAutomationEntitiesMono();

        StepVerifier.create(result)
                .assertNext(list -> assertEquals(1, list.size()))
                .verifyComplete();
    }

    @Test
    void testGetSystemMetricsOffloaded() {
        StepVerifier.create(automationService.getSystemMetrics())
                .assertNext(metrics -> {
                    assertNotNull(metrics);
                    assertNotNull(metrics.get("uptime"));
                })
                .verifyComplete();
    }
}
