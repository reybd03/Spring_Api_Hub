package com.spring.api.automation.controller;

import com.spring.api.automation.entity.AutomationEntity;
import com.spring.api.automation.service.AutomationService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;

@RestController
@RequestMapping("/automation")
public class AutomationController {

    private final AutomationService automationService;

    public AutomationController(AutomationService automationService) {
        this.automationService = automationService;
    }

    @GetMapping
    public Flux<AutomationEntity> getAllAutomationEntities() {
        return automationService.findAllMono()
                .flatMapMany(Flux::fromIterable);
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<AutomationEntity>> getAutomationEntityById(@PathVariable Long id) {
        return automationService.findByIdMono(id)
                .map(entity -> entity.map(ResponseEntity::ok)
                        .orElse(ResponseEntity.notFound().build()));
    }

    @PostMapping
    public Mono<ResponseEntity<AutomationEntity>> createAutomationEntity(@RequestBody AutomationEntity entity) {
        return automationService.createAutomationEntityMono(entity)
                .map(created -> ResponseEntity.status(HttpStatus.CREATED).body(created))
                .onErrorResume(e -> Mono.just(badRequestEntity()));
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<AutomationEntity>> updateAutomationEntity(@PathVariable Long id,
                                                                          @RequestBody AutomationEntity entity) {
        return automationService.findByIdMono(id)
                .flatMap(existing -> {
                    if (existing.isEmpty()) {
                        return Mono.just(notFoundEntity());
                    }
                    AutomationEntity toUpdate = existing.get();
                    toUpdate.setProductName(entity.getProductName());
                    toUpdate.setProductDiscoveryStatus(entity.getProductDiscoveryStatus());
                    toUpdate.setProductDiscovered(entity.getProductDiscovered());
                    toUpdate.setProductBasePath(entity.getProductBasePath());
                    toUpdate.setProductUserName(entity.getProductUserName());
                    toUpdate.setProductPassword(entity.getProductPassword());
                    toUpdate.setProductAPIKey(entity.getProductAPIKey());
                    toUpdate.setProductURL(entity.getProductURL());
                    toUpdate.setProductPort(entity.getProductPort());
                    toUpdate.setProductStatus(entity.getProductStatus());
                    toUpdate.setProductFunctions(entity.getProductFunctions());
                    toUpdate.setProductActions(entity.getProductActions());
                    return automationService.saveMono(toUpdate)
                            .map(ResponseEntity::ok);
                })
                .onErrorResume(e -> Mono.just(badRequestEntity()));
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteAutomationEntity(@PathVariable Long id) {
        return automationService.findByIdMono(id)
                .flatMap(existing -> {
                    if (existing.isEmpty()) {
                        return Mono.just(ResponseEntity.notFound().build());
                    }
                    return automationService.deleteByIdMono(id)
                            .then(Mono.just(ResponseEntity.noContent().build()));
                });
    }

    private static ResponseEntity<AutomationEntity> badRequestEntity() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).<AutomationEntity>build();
    }

    private static ResponseEntity<AutomationEntity> notFoundEntity() {
        return ResponseEntity.<AutomationEntity>notFound().build();
    }
}