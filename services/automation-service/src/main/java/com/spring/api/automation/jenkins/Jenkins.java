package com.spring.api.automation.jenkins;

import com.spring.api.automation.entity.AutomationEntity;
import com.spring.api.automation.repository.AutomationRepository;
import com.spring.api.automation.service.AutomationActions;
import com.spring.api.automation.service.AutomationService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class Jenkins {

    public static final String PRODUCT_NAME = "Jenkins";

    @Value("${automation.jenkins.default-url}")
    private String defaultUrl;

    @Value("${automation.jenkins.default-port}")
    private String defaultPort;

    @Value("${automation.jenkins.default-username}")
    private String defaultUsername;

    @Value("${automation.jenkins.default-password}")
    private String defaultPassword;

    @Value("${automation.jenkins.default-base-path}")
    private String defaultBasePath;

    private final AutomationRepository automationRepository;
    private final AutomationService automationService;
    private final WebClient webClient;
    private final AutomationActions automationActions;
    private final JenkinsDiscovery jenkinsDiscovery;

    public Jenkins(AutomationRepository automationRepository,
                   AutomationService automationService,
                   WebClient.Builder webClientBuilder,
                   AutomationActions automationActions,
                   JenkinsDiscovery jenkinsDiscovery) {
        this.automationRepository = automationRepository;
        this.automationService = automationService;
        this.webClient = webClientBuilder.build();
        this.automationActions = automationActions;
        this.jenkinsDiscovery = jenkinsDiscovery;
    }

    public AutomationEntity fetchJenkinsEntity() {
        Optional<AutomationEntity> jenkinsEntityOptional = automationRepository.findByProductName(PRODUCT_NAME);
        return jenkinsEntityOptional.orElse(null);
    }

    public AutomationEntity verifyJenkinsEntity(AutomationEntity jenkinsEntity) {
        Optional<AutomationEntity> jenkinsEntityOptional = automationRepository.findByProductName(PRODUCT_NAME);
        if (jenkinsEntityOptional.isPresent()) {
            return jenkinsEntityOptional.get();
        } else {
            jenkinsEntity.setProductBasePath(defaultBasePath);
            jenkinsEntity.setProductURL(defaultUrl);
            jenkinsEntity.setProductPort(defaultPort);
            jenkinsEntity.setProductUserName(defaultUsername);
            jenkinsEntity.setProductPassword(defaultPassword);
            return automationService.createAutomationEntity(jenkinsEntity);
        }
    }

    public AutomationEntity discoverJenkins(AutomationEntity jenkinsEntity) {
        String jenkinsBasePath = jenkinsDiscovery.discoverDefaultJenkinsPath();
        if (!jenkinsBasePath.equals("Not Found")) {
            jenkinsEntity.setProductBasePath(jenkinsBasePath);
            jenkinsEntity.setProductDiscovered(true);
            jenkinsEntity.setProductDiscoveryStatus("Completed");
            if (!automationActions.isProcessRunning(PRODUCT_NAME)) {
                jenkinsEntity.setProductStatus("Stopped");
            }
            jenkinsEntity = automationRepository.save(jenkinsEntity);
        } else if (jenkinsEntity.getProductDiscoveryStatus().equals("Pending_Discovery")) {
            jenkinsEntity.setProductDiscoveryStatus("Failed");
            jenkinsEntity.setProductDiscovered(false);
            jenkinsEntity = automationRepository.save(jenkinsEntity);
        }
        return jenkinsEntity;
    }

    public AutomationEntity discoverJenkinsWithPath(AutomationEntity jenkinsEntity, String basePath) {
        String jenkinsBasePath = jenkinsDiscovery.findJenkinsBasePath(basePath);

        jenkinsEntity.setProductDiscoveryStatus("Discovering");
        jenkinsEntity = automationRepository.save(jenkinsEntity);

        if (!jenkinsBasePath.equals("Not Found")) {
            jenkinsEntity.setProductBasePath(jenkinsBasePath);
            jenkinsEntity.setProductDiscovered(true);
            jenkinsEntity.setProductDiscoveryStatus("Completed");
            if (!automationActions.isProcessRunning(PRODUCT_NAME)) {
                jenkinsEntity.setProductStatus("Stopped");
            }
            jenkinsEntity = automationRepository.save(jenkinsEntity);
        } else {
            jenkinsEntity.setProductDiscoveryStatus("Failed");
            jenkinsEntity.setProductDiscovered(false);
            jenkinsEntity = automationRepository.save(jenkinsEntity);
        }
        return jenkinsEntity;
    }

    public String updateJenkinsConfig(Map<String, String> updateMap) {
        AutomationEntity jenkinsEntity = fetchJenkinsEntity();
        if (jenkinsEntity == null) {
            jenkinsEntity = getJenkinsDetails();
        }

        for (Map.Entry<String, String> entry : updateMap.entrySet()) {
            String updateKey = entry.getKey();
            String updateValue = entry.getValue();

            switch (updateKey) {
                case "productBasePath":
                    jenkinsEntity.setProductDiscoveryStatus("Pending_Discovery");
                    jenkinsEntity = automationRepository.save(jenkinsEntity);
                    jenkinsEntity = discoverJenkinsWithPath(jenkinsEntity, updateValue);
                    break;
                case "productURL":
                case "productPort":
                case "productUserName":
                case "productPassword":
                case "productAPIKey":
                    jenkinsEntity = automationService.updateAutomationEntity(jenkinsEntity, updateKey, updateValue);
                    break;
                default:
                    System.out.println("Unknown update key: " + updateKey);
            }
        }

        return updateMap.keySet().stream().findFirst().orElse("Invalid_Key");
    }

    public AutomationEntity getJenkinsDetails() {
        AutomationEntity jenkinsEntity = new AutomationEntity();
        jenkinsEntity.setProductName(PRODUCT_NAME);

        jenkinsEntity = verifyJenkinsEntity(jenkinsEntity);
        if (!jenkinsEntity.getProductDiscovered()) {
            jenkinsEntity = discoverJenkins(jenkinsEntity);
        }
        return jenkinsEntity;
    }

    public Mono<Void> toggleService(String action) {
        String urlPath = "start".equalsIgnoreCase(action) ? "/safeRestart" : "/exit";
        String url = webClient.mutate().baseUrl(defaultUrl + ":" + defaultPort).build().toString();

        return webClient.post()
                .uri(urlPath)
                .headers(headers -> headers.setBasicAuth(defaultUsername, defaultPassword))
                .retrieve()
                .bodyToMono(Void.class)
                .onErrorResume(e -> Mono.empty());
    }

    public Mono<Map<String, Object>> getSystemMetrics() {
        return automationService.getSystemMetrics();
    }
}