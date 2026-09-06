package com.spring.api.hub.products.automation.jenkins;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

import org.thymeleaf.spring6.context.webflux.IReactiveDataDriverContextVariable;
import org.thymeleaf.spring6.context.webflux.ReactiveDataDriverContextVariable;

import java.time.Duration;
import java.util.Map;

import com.spring.api.hub.WebController;
import com.spring.api.hub.products.automation.AutomationEntity;
import com.spring.api.hub.products.automation.AutomationRepository;
import com.spring.api.hub.products.automation.AutomationService;

@Controller
@RequestMapping("/products/automation/jenkins")
public class JenkinsController {

    private final WebController webController;
    private final Jenkins jenkins;
    private final JenkinsExecutorService jenkinsExecutorService;
    private final AutomationRepository automationRepository;
    private final AutomationService automationService;
    // private final AutomationEntity jenkinsEntity;

    JenkinsController(WebController webController, Jenkins jenkins, AutomationRepository automationRepository,
            AutomationService automationService, JenkinsExecutorService jenkinsExecutorService) {
        this.webController = webController;
        this.jenkins = jenkins;
        this.automationRepository = automationRepository;
        this.automationService = automationService;
        this.jenkinsExecutorService = jenkinsExecutorService;
    }

    private Mono<Map<String, Object>> getJenkinsConfigMono() {
        return Mono.fromFuture(java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            Map<String, Object> jenkinsConfig = new java.util.HashMap<>();
            AutomationEntity jenkinsEntity = jenkins.getJenkinsDetails();

            if (jenkinsEntity.getProductDiscoveryStatus().equals("Pending_Discovery")
                    && jenkinsEntity.getProductBasePath().equals("/")) {
                jenkinsEntity = jenkins.discoverJenkins(jenkinsEntity);
            }
            jenkinsConfig.put("productName", jenkinsEntity.getProductName());
            jenkinsConfig.put("productDiscovered", jenkinsEntity.getProductDiscovered());
            jenkinsConfig.put("productDiscoveryStatus", jenkinsEntity.getProductDiscoveryStatus());
            jenkinsConfig.put("productBasePath", jenkinsEntity.getProductBasePath());
            jenkinsConfig.put("productUserName", jenkinsEntity.getProductUserName());
            jenkinsConfig.put("productPassword", jenkinsEntity.getProductPassword());
            jenkinsConfig.put("productAPIKey", jenkinsEntity.getProductAPIKey());
            jenkinsConfig.put("productURL", jenkinsEntity.getProductURL());
            jenkinsConfig.put("productPort", jenkinsEntity.getProductPort());
            return jenkinsConfig;
        }));
    }

    // Renders the baseline static template
    @GetMapping
    public Mono<String> JenkinsPage(final Model model) {
        // Fetch product details on page load
        AutomationEntity jenkinsConfig = jenkins.getJenkinsDetails();

        // Flux<AutomationEntity> flux = jenkins.getProductDetails();

        // automationService.getProductUpdates().log().subscribe(value ->
        // System.out.println("the value " + value));

        // IReactiveDataDriverContextVariable reactiveJenkins = new
        // ReactiveDataDriverContextVariable(flux, 1);
        model.addAttribute("title", "Jenkins");
        model.addAttribute("jenkinsConfig", jenkinsConfig);
        // model.addAttribute("productDetails", reactiveJenkins);

        // System.out.println("the model value " + model.asMap());

        return Mono.just("pages");
    }

    @PostMapping("/updateConfigs")
    @ResponseBody
    public Mono<Map<String, String>> updateJenkinsProduct(@RequestParam Map<String, String> updateMap) {
        Mono<Void> updateOperation = Mono.empty();

        // if ("docker".equalsIgnoreCase(target)) {
        // updateOperation = dockerService.toggleService(action);
        // } else if ("jenkins".equalsIgnoreCase(target)) {
        // updateOperation = jenkinsService.toggleService(action);
        // }
        String target = jenkins.unpackJenkinsMap(updateMap);
        // System.out.println("Target: " + target);

        return updateOperation
                .then(Mono.just(Map.of("status", "Success", "message", target + " executed.")))
                .onErrorResume(e -> Mono.just(Map.of("status", "Error", "message", e.getMessage())));

        // return ResponseEntity.ok("{\"message\": \"Received Jenkins Updates!\"}");
    }

    // Jenkins Product Actions
    @PostMapping("/actions")
    @ResponseBody
    public Mono<Map<String, String>> jenkinsProductActions(@RequestParam Map<String, String> actionMap) {
        Mono<Void> actionOperation = Mono.empty();

        String target = actionMap.getOrDefault("target", "Invalid_Value");
        String action = actionMap.getOrDefault("action", "Invalid_Value");
        System.out.println("Target: " + target);
        System.out.println("Action: " + action);

        jenkinsExecutorService.actionGateway(target, action);

        return actionOperation
                .then(Mono.just(Map.of("status", "Success", "message", "Jenkins actions executed.")))
                .onErrorResume(e -> Mono.just(Map.of("status", "Error", "message", e.getMessage())));

    }

    // Produces the continuous Server-Sent Event stream for real-time resource data
    @GetMapping(value = "/streamSysStats", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    public Flux<ServerSentEvent<Map<String, Object>>> streamJenkinsSysStats() {
        return Flux.interval(Duration.ofSeconds(1)) // Poll metrics every second
                .flatMap(tick -> automationService.getSystemMetrics())
                .map(data -> ServerSentEvent.<Map<String, Object>>builder()
                        .id(String.valueOf(System.currentTimeMillis()))
                        .event("sys-stats")
                        .data(data)
                        .build());
    }

    // Stream Jenkin configuration details
    @GetMapping(value = "/streamConfig", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    public Flux<ServerSentEvent<Map<String, Object>>> streamJenkinsConfig() {
        return Flux.interval(Duration.ofSeconds(1)) // Poll metrics every second
                .flatMap(tick -> getJenkinsConfigMono())
                .map(data -> ServerSentEvent.<Map<String, Object>>builder()
                        .id(String.valueOf(System.currentTimeMillis()))
                        .event("config")
                        .data(data)
                        .build());
    }

    // TODO: Stream Jenkins nodes and jobs

}
