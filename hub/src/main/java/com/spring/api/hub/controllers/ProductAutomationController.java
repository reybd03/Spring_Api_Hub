package com.spring.api.hub.controllers;

import com.spring.api.hub.products.automation.AutomationEntity;
import com.spring.api.hub.products.automation.AutomationService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/products/automation")
public class ProductAutomationController {
    private final AutomationService automationService;

    public ProductAutomationController(AutomationService automationService) {
        this.automationService = automationService;
    }

    @GetMapping
    public Flux<AutomationEntity> getAllAutomationEntities() {
        return automationService.findAllAutomationEntitiesMono()
                .flatMapMany(Flux::fromIterable);
    }
}
