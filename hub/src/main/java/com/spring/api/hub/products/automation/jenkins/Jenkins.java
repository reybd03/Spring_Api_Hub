package com.spring.api.hub.products.automation.jenkins;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

import com.spring.api.hub.products.automation.AutomationStateEvent;
import com.spring.api.hub.products.automation.jenkins.JenkinsDiscovery;
// import com.spring.api.hub.products.automation.AutomationHandler;
import com.spring.api.hub.products.automation.AutomationRepository;
import com.spring.api.hub.products.automation.AutomationEntity;
import com.spring.api.hub.products.automation.AutomationService;

@Component
public class Jenkins {

    public final String productName = "Jenkins";
    public final String defaultURL = "http://localhost";
    public final String defaultPort = "8080";
    public final String defaultUsername = "user";
    public final String defaultPassword = "password";
    public final String defaultBasePath = "/";
    // Abstract method to let subclasses define how properties map during an update

    private final AutomationRepository automationRepository;
    private final AutomationService automationService;
    private final WebClient webClient;

    public Jenkins(AutomationRepository automationRepository, AutomationService automationService,
            WebClient.Builder webClientBuilder) {
        this.automationRepository = automationRepository;
        this.automationService = automationService;
        this.webClient = webClientBuilder.baseUrl("http://localhost:8080").build();
    }

    public AutomationEntity verifyJenkinsEntity(AutomationEntity jenkinsEntity) {
        Optional<AutomationEntity> jenkinsEntityOptional = automationRepository.findByProductName(productName);
        // automationService.prettyPrintEntity(jenkinsEntityOptional.get());
        if (jenkinsEntityOptional.isPresent()) {
            jenkinsEntity = jenkinsEntityOptional.get();
        } else {
            jenkinsEntity.setProductBasePath(defaultBasePath);
            jenkinsEntity.setProductURL(defaultURL);
            jenkinsEntity.setProductPort(defaultPort);
            jenkinsEntity.setProductUserName(defaultUsername);
            jenkinsEntity.setProductPassword(defaultPassword);
            jenkinsEntity = automationService.createAutomationEntity(jenkinsEntity);
        }

        automationService.prettyPrintEntity(jenkinsEntity);
        return jenkinsEntity;
    }

    public void discoverJenkins(AutomationEntity jenkinsEntity) {

    }

    public void discoverJenkins(AutomationEntity jenkinsEntity, String basePath) {

    }

    public AutomationEntity getJenkinsDetails() {
        AutomationEntity jenkinsEntity = new AutomationEntity();
        jenkinsEntity.setProductName(productName);
        jenkinsEntity = verifyJenkinsEntity(jenkinsEntity);

        discoverJenkins(jenkinsEntity);

        return jenkinsEntity;
    }

    public Mono<Void> toggleService(String action) {
        String urlPath = "start".equalsIgnoreCase(action) ? "/safeRestart" : "/exit";

        return this.webClient.post()
                .uri(urlPath)
                .headers(headers -> headers.setBasicAuth("admin", "api_token_here"))
                .retrieve()
                .bodyToMono(Void.class)
                .onErrorResume(e -> Mono.empty()); // Fallback handling
    }

}
