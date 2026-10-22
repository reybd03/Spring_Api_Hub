package com.spring.api.automation.jenkins;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Component
public class JenkinsDiscovery {

    @Value("${automation.jenkins.discovery-paths}")
    private List<String> discoveryPaths;

    public String findJenkinsBasePath(String basePath) {
        if (basePath == null || basePath.isBlank()) {
            return "Not Found";
        }

        Path currentDir = Paths.get("").toAbsolutePath();
        Path updatedJenkinsDir = currentDir.resolve(basePath);

        try {
            if (Files.exists(updatedJenkinsDir) && Files.isDirectory(updatedJenkinsDir)) {
                if (Files.exists(updatedJenkinsDir.resolve("config.xml"))) {
                    return updatedJenkinsDir.toAbsolutePath().toString();
                }
                if (Files.exists(updatedJenkinsDir.resolve("dockerfile"))) {
                    return updatedJenkinsDir.toAbsolutePath().toString();
                }
                if (Files.exists(updatedJenkinsDir.resolve("docker-compose.yml")) ||
                        Files.exists(updatedJenkinsDir.resolve("docker-compose.yaml"))) {
                    return updatedJenkinsDir.toAbsolutePath().toString();
                }
            }
        } catch (Exception e) {
            System.out.println("Error checking custom path: " + e.getMessage());
        }

        return "Not Found";
    }

    public String discoverDefaultJenkinsPath() {
        for (String path : discoveryPaths) {
            try {
                String expandedPath = path.replace("${user.home}", System.getProperty("user.home"));
                Path jenkinsHome = Paths.get(expandedPath);

                if (Files.exists(jenkinsHome) && Files.isDirectory(jenkinsHome)) {
                    if (Files.exists(jenkinsHome.resolve("config.xml"))) {
                        return jenkinsHome.toAbsolutePath().toString();
                    }
                    if (Files.exists(jenkinsHome.resolve("dockerfile"))) {
                        return jenkinsHome.toAbsolutePath().toString();
                    }
                    if (Files.exists(jenkinsHome.resolve("docker-compose.yml")) ||
                            Files.exists(jenkinsHome.resolve("docker-compose.yaml"))) {
                        return jenkinsHome.toAbsolutePath().toString();
                    }
                }
            } catch (Exception e) {
                System.out.println("Error checking path " + path + ": " + e.getMessage());
            }
        }
        return "Not Found";
    }
}