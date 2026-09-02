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
    private final AutomationRepository automationRepository;
    private final AutomationService automationService;
    // private final AutomationEntity jenkinsEntity;

    JenkinsController(WebController webController, Jenkins jenkins, AutomationRepository automationRepository,
            AutomationService automationService) {
        this.webController = webController;
        this.jenkins = jenkins;
        this.automationRepository = automationRepository;
        this.automationService = automationService;
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
    public Mono<Map<String, String>> updateJenkinsProduct(@RequestParam String target, @RequestParam String action) {
        Mono<Void> updateOperation = Mono.empty();

        // TODO: Implement switch case logic to update Jenkins product
        // if ("docker".equalsIgnoreCase(target)) {
        // updateOperation = dockerService.toggleService(action);
        // } else if ("jenkins".equalsIgnoreCase(target)) {
        // updateOperation = jenkinsService.toggleService(action);
        // }

        return updateOperation
                .then(Mono.just(Map.of("status", "Success", "message", target + " " + action + " executed.")))
                .onErrorResume(e -> Mono.just(Map.of("status", "Error", "message", e.getMessage())));

        // return ResponseEntity.ok("{\"message\": \"Received Jenkins Updates!\"}");
    }

    // Produces the continuous Server-Sent Event stream for real-time resource data
    @GetMapping(value = "/streamSysStats", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    public Flux<ServerSentEvent<Map<String, Object>>> streamJenkinsSysStats() {
        return automationService.fluxBoilerplate(automationService.getSystemMetrics(), "sys-stats");
    }

    // Stream Jenkin configuration details
    @GetMapping(value = "/streamConfig", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    public Flux<ServerSentEvent<Map<String, Object>>> streamJenkinsConfig() {
        return automationService.fluxBoilerplate(automationService.getSystemMetrics(), "jenkins-config");
    }

}
