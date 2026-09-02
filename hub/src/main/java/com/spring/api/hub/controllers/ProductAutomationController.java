package com.spring.api.hub.controllers;

import com.spring.api.hub.WebController;
import com.spring.api.hub.products.automation.AutomationEntity;
import com.spring.api.hub.products.automation.AutomationService;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.ui.Model;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Controller
@RequestMapping("/products/automation")
public class ProductAutomationController {
    private final WebController webController;
    private final AutomationService automationService;

    ProductAutomationController(WebController webController, AutomationService automationService) {
        this.webController = webController;
        this.automationService = automationService;
    }

    // @PostMapping
    // public Mono<AutomationEntity> createAutomationProduct(@RequestBody
    // AutomationEntity automationEntity) {
    // return automationService.createAutomationProduct(automationEntity);
    // }

    // @PutMapping
    // public Mono<AutomationEntity> updateAutomationProduct(Long id, @RequestBody
    // AutomationEntity automationEntity) {
    // return automationService.updateProduct(id, automationEntity);
    // }

    // Server-Sent Events (SSE) Endpoint for live page updates
    // @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    // public Flux<AutomationEntity> streamProjects() {
    // return automationService.getProductUpdates();
    // }

    // // Endpoint to execute updates
    // @PutMapping("/{id}")
    // public Mono<AutomationEntity> updateAutomationProduct(@PathVariable Long id,
    // @RequestBody AutomationEntity automationEntity) {
    // return abstractAutomationService.updateProduct(id, automationEntity);
    // }

    // // SSE Endpoint to push live updates to the broswer view
    // @GetMapping(value = "/updates", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    // public Flux<AutomationEntity> getProductUpdates() {
    // return abstractAutomationService.getProductUpdates();
    // }
}
