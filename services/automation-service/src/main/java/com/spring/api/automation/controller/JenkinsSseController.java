package com.spring.api.automation.controller;

import com.spring.api.automation.jenkins.Jenkins;
import com.spring.api.automation.service.AutomationService;

import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/jenkins/stream")
public class JenkinsSseController {

    private final Jenkins jenkins;
    private final AutomationService automationService;

    public JenkinsSseController(Jenkins jenkins, AutomationService automationService) {
        this.jenkins = jenkins;
        this.automationService = automationService;
    }

    @GetMapping(value = "/sys-stats", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Map<String, Object>>> streamSystemStats() {
        return Flux.interval(Duration.ofSeconds(1))
                .flatMap(tick -> automationService.getSystemMetrics())
                .map(data -> ServerSentEvent.<Map<String, Object>>builder()
                        .id(String.valueOf(System.currentTimeMillis()))
                        .event("sys-stats")
                        .data(data)
                        .build());
    }

    @GetMapping(value = "/config", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Map<String, Object>>> streamJenkinsConfig() {
        return Flux.interval(Duration.ofSeconds(1))
                .flatMap(tick -> Mono.fromCallable(() -> jenkins.getJenkinsDetails())
                        .subscribeOn(Schedulers.boundedElastic())
                        .map(entity -> {
                            Map<String, Object> config = new java.util.HashMap<>();
                            config.put("productName", entity.getProductName());
                            config.put("productDiscovered", entity.getProductDiscovered());
                            config.put("productDiscoveryStatus", entity.getProductDiscoveryStatus());
                            config.put("productBasePath", entity.getProductBasePath());
                            config.put("productUserName", entity.getProductUserName());
                            config.put("productPassword", entity.getProductPassword());
                            config.put("productAPIKey", entity.getProductAPIKey());
                            config.put("productURL", entity.getProductURL());
                            config.put("productPort", entity.getProductPort());
                            return config;
                        }))
                .map(data -> ServerSentEvent.<Map<String, Object>>builder()
                        .id(String.valueOf(System.currentTimeMillis()))
                        .event("config")
                        .data(data)
                        .build());
    }
}