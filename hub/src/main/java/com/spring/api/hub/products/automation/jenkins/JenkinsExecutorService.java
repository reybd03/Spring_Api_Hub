package com.spring.api.hub.products.automation.jenkins;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.spring.api.hub.WebController;
import com.spring.api.hub.products.automation.AutomationActions;
import com.spring.api.hub.products.automation.AutomationEntity;
import com.spring.api.hub.products.automation.AutomationRepository;
import com.spring.api.hub.products.automation.AutomationService;

@Service
public class JenkinsExecutorService {
    private final WebController webController;
    private final AutomationRepository automationRepository;
    private final Jenkins jenkins;
    private final AutomationActions automationActions;
    private final AutomationService automationService;

    public JenkinsExecutorService(WebController webController, AutomationRepository automationRepository,
            Jenkins jenkins, AutomationActions automationActions, AutomationService automationService) {
        this.webController = webController;
        this.automationRepository = automationRepository;
        this.jenkins = jenkins;
        this.automationActions = automationActions;
        this.automationService = automationService;
    }

    /**
     * Manage Jenkins environment with Docker Compose
     *
     * @param target product target name
     * @param action "start", "stop", or "restart"
     */
    public String actionGateway(String target, String action) {
        System.out.println("Target: " + target);
        System.out.println("Action: " + action);

        AutomationEntity jenkinsEntity = jenkins.fetchJenkinsEntity();
        if (jenkinsEntity == null) {
            jenkinsEntity = jenkins.getJenkinsDetails();
        }
        String jenkinsBasePath = jenkinsEntity.getProductBasePath();

        List<String> command = new ArrayList<>();

        switch (action.toLowerCase()) {
            case "start" -> {
                command.add("docker");
                command.add("compose");
                command.add("up");
                command.add("-d");
                Long pid = automationActions.executeProductProcess(command, jenkinsBasePath, action);
                boolean running = automationActions.isComposeRunning(jenkinsBasePath) || automationActions.isPidRunning(pid);
                jenkinsEntity.setProductStatus(running ? "Running" : "Failed");
                jenkinsEntity.setProductProcessId(running ? pid : null);
            }
            case "stop" -> {
                command.add("docker");
                command.add("compose");
                command.add("stop");
                Long pid = automationActions.executeProductProcess(command, jenkinsBasePath, action);
                boolean running = automationActions.isComposeRunning(jenkinsBasePath) || automationActions.isPidRunning(jenkinsEntity.getProductProcessId());
                jenkinsEntity.setProductStatus(running ? "Failed" : "Stopped");
                jenkinsEntity.setProductProcessId(running ? jenkinsEntity.getProductProcessId() : null);
            }
            case "restart" -> {
                command.add("docker");
                command.add("compose");
                command.add("restart");
                Long pid = automationActions.executeProductProcess(command, jenkinsBasePath, action);
                boolean running = automationActions.isComposeRunning(jenkinsBasePath) || automationActions.isPidRunning(pid);
                jenkinsEntity.setProductStatus(running ? "Running" : "Failed");
                jenkinsEntity.setProductProcessId(running ? pid : null);
            }
            default -> {
                System.out.println("Invalid action: " + action);
                return "Invalid Action";
            }
        }

        automationService.prettyPrintEntity(jenkinsEntity);
        automationRepository.save(jenkinsEntity);
        return "Success";
    }
}
