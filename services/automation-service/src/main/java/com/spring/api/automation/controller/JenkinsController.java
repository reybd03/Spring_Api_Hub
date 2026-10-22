package com.spring.api.automation.controller;

import com.spring.api.automation.entity.AutomationEntity;
import com.spring.api.automation.jenkins.Jenkins;
import com.spring.api.automation.jenkins.JenkinsExecutorService;
import com.spring.api.automation.service.AutomationService;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;

@RestController
@RequestMapping("/jenkins")
public class JenkinsController {

    private final Jenkins jenkins;
    private final JenkinsExecutorService jenkinsExecutorService;
    private final AutomationService automationService;

    public JenkinsController(Jenkins jenkins,
                             JenkinsExecutorService jenkinsExecutorService,
                             AutomationService automationService) {
        this.jenkins = jenkins;
        this.jenkinsExecutorService = jenkinsExecutorService;
        this.automationService = automationService;
    }

    @GetMapping
    public Mono<ResponseEntity<AutomationEntity>> getJenkinsDetails() {
        return Mono.fromCallable(() -> jenkins.getJenkinsDetails())
                .subscribeOn(Schedulers.boundedElastic())
                .map(ResponseEntity::ok)
                .onErrorResume(e -> Mono.just(ResponseEntity.status(500).build()));
    }

    @GetMapping("/config")
    public Mono<ResponseEntity<Map<String, Object>>> getJenkinsConfig() {
        return Mono.fromCallable(() -> jenkins.getJenkinsDetails())
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
                    return ResponseEntity.ok(config);
                })
                .onErrorResume(e -> Mono.just(ResponseEntity.status(500).build()));
    }

    @PostMapping("/config")
    public Mono<ResponseEntity<Map<String, String>>> updateJenkinsConfig(@RequestBody Map<String, String> updateMap) {
        return Mono.fromCallable(() -> jenkins.updateJenkinsConfig(updateMap))
                .subscribeOn(Schedulers.boundedElastic())
                .map(key -> {
                    Map<String, String> response = Map.of(
                            "status", "Success",
                            "message", key + " updated",
                            "updatedKey", key
                    );
                    return ResponseEntity.ok(response);
                })
                .onErrorResume(e -> Mono.just(ResponseEntity.badRequest().build()));
    }

    @PostMapping("/actions")
    public Mono<ResponseEntity<Map<String, String>>> executeJenkinsAction(@RequestBody Map<String, String> actionMap) {
        String action = actionMap.getOrDefault("action", "invalid");
        return Mono.fromCallable(() -> jenkinsExecutorService.executeAction(action))
                .subscribeOn(Schedulers.boundedElastic())
                .map(result -> {
                    Map<String, String> response = Map.of(
                            "status", "Success".equals(result) ? "Success" : "Error",
                            "message", result
                    );
                    return ResponseEntity.ok(response);
                });
    }

    @PostMapping("/discover")
    public Mono<ResponseEntity<AutomationEntity>> triggerDiscovery() {
        return Mono.fromCallable(() -> jenkins.getJenkinsDetails())
                .subscribeOn(Schedulers.boundedElastic())
                .map(jenkins::discoverJenkins)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> Mono.just(ResponseEntity.status(500).build()));
    }
}