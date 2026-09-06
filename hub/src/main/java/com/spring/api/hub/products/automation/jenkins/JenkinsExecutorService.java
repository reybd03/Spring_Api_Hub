package com.spring.api.hub.products.automation.jenkins;

import org.springframework.stereotype.Service;

import com.spring.api.hub.WebController;

@Service
public class JenkinsExecutorService {
    private final WebController webController;

    public JenkinsExecutorService(WebController webController) {
        this.webController = webController;
    }

    public void actionGateway(String target, String action) {
        System.out.println("Target: " + target);
        System.out.println("Action: " + action);
    }

}
