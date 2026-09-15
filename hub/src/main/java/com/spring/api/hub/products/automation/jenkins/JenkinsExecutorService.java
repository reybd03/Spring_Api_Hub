package com.spring.api.hub.products.automation.jenkins;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.spring.api.hub.WebController;
import com.spring.api.hub.products.automation.AutomationActions;
import com.spring.api.hub.products.automation.AutomationEntity;
import com.spring.api.hub.products.automation.AutomationRepository;
import com.spring.api.hub.products.automation.AutomationService;
import com.spring.api.hub.products.automation.jenkins.Jenkins;

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
     * Initiliaze Jenkins environment with Docker Compose
     * ("docker", "compose", "up", "-d")
     * 
     * @param target
     * @param action
     */
    public String actionGateway(String target, String action) {
        System.out.println("Target: " + target);
        System.out.println("Action: " + action);

        AutomationEntity jenkinsEntity = jenkins.fetchJenkinsEntity();
        String jenkinsName = target;
        String jenkinsBasePath = jenkinsEntity.getProductBasePath();
        Long jenkinsPid = jenkinsEntity.getProductProcessId();
        String jenkinsStatus = jenkinsEntity.getProductStatus();

        List<String> command = new ArrayList<>();

        if (!automationActions.isProcessRunning(jenkinsName)) {
            jenkinsEntity.setProductStatus("Stopped");
        }

        switch (jenkinsStatus) {
            case "Stopped", "Unknown", "Failed" -> {
                command.add("docker");
                command.add("compose");
                command.add("up");
                command.add("-d");
                jenkinsPid = automationActions.executeProductProcess(command, jenkinsBasePath, action);
                jenkinsEntity.setProductStatus("Running");
                jenkinsEntity.setProductProcessId(jenkinsPid);
            }
            case "Running", "Starting", "Stopping", "Restarting" -> {
                switch (action) {
                    case "stop" -> {
                        command.add("docker");
                        command.add("compose");
                        command.add("stop");
                        jenkinsPid = automationActions.executeProductProcess(command, jenkinsBasePath, action);
                        jenkinsEntity.setProductStatus("Stopped");
                        jenkinsEntity.setProductProcessId(jenkinsPid);
                    }
                    case "restart" -> {
                        command.add("docker");
                        command.add("compose");
                        command.add("restart");
                        jenkinsPid = automationActions.executeProductProcess(command, jenkinsBasePath, action);
                        jenkinsEntity.setProductStatus("Running"); // TODO: Check Status from PID via Logging
                        jenkinsEntity.setProductProcessId(jenkinsPid);
                    }
                    default -> {
                        System.out.println("Invalid action");
                    }
                }
            }
            default -> {
                System.out.println("Invalid jenkins status");
            }
        }
        automationService.prettyPrintEntity(jenkinsEntity);
        jenkinsEntity = automationRepository.save(jenkinsEntity);
        return "Success";
    }

}
