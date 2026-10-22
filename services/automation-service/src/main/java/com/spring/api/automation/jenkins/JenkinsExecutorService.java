package com.spring.api.automation.jenkins;

import com.spring.api.automation.entity.AutomationEntity;
import com.spring.api.automation.repository.AutomationRepository;
import com.spring.api.automation.service.AutomationActions;
import com.spring.api.automation.service.AutomationService;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class JenkinsExecutorService {

    private final AutomationRepository automationRepository;
    private final Jenkins jenkins;
    private final AutomationActions automationActions;
    private final AutomationService automationService;

    public JenkinsExecutorService(AutomationRepository automationRepository,
                                  Jenkins jenkins,
                                  AutomationActions automationActions,
                                  AutomationService automationService) {
        this.automationRepository = automationRepository;
        this.jenkins = jenkins;
        this.automationActions = automationActions;
        this.automationService = automationService;
    }

    public String executeAction(String action) {
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
                Long pid = automationActions.executeProductProcess(command, jenkinsBasePath);
                boolean running = automationActions.isComposeRunning(jenkinsBasePath) || automationActions.isPidRunning(pid);
                jenkinsEntity.setProductStatus(running ? "Running" : "Failed");
                jenkinsEntity.setProductProcessId(running ? pid : null);
            }
            case "stop" -> {
                command.add("docker");
                command.add("compose");
                command.add("stop");
                Long pid = automationActions.executeProductProcess(command, jenkinsBasePath);
                boolean running = automationActions.isComposeRunning(jenkinsBasePath) || automationActions.isPidRunning(jenkinsEntity.getProductProcessId());
                jenkinsEntity.setProductStatus(running ? "Failed" : "Stopped");
                jenkinsEntity.setProductProcessId(running ? jenkinsEntity.getProductProcessId() : null);
            }
            case "restart" -> {
                command.add("docker");
                command.add("compose");
                command.add("restart");
                Long pid = automationActions.executeProductProcess(command, jenkinsBasePath);
                boolean running = automationActions.isComposeRunning(jenkinsBasePath) || automationActions.isPidRunning(pid);
                jenkinsEntity.setProductStatus(running ? "Running" : "Failed");
                jenkinsEntity.setProductProcessId(running ? pid : null);
            }
            default -> {
                System.out.println("Invalid action: " + action);
                return "Invalid Action";
            }
        }

        automationRepository.save(jenkinsEntity);
        return "Success";
    }
}