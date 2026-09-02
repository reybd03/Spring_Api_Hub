package com.spring.api.hub.products.automation.jenkins;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.stream.Stream;

public class JenkinsDiscovery {
    // Common default installation paths for Jenkins Home
    private static final String[] JENKINS_HOME_PATHS = {
            "/var/lib/jenkins",
            "/usr/share/jenkins",
            "/usr/local/share/jenkins",
            "/opt/jenkins",
            "C:\\ProgramData\\Jenkins\\.jenkins", // Windows (Modern installer default)
            "C:\\Users\\" + System.getProperty("user.name") + "\\.jenkins", // Windows/Mac (Manual war run)
            "/Users/" + System.getProperty("user.name") + "/.jenkins" // macOS/Linux (Common manual run)
    };

    public String updatedJenkinsBasePath(String ProductBasePath) throws IOException {
        // 1. Get the current working directory path
        Path currentDir = Paths.get("").toAbsolutePath();
        Path updatedJenkinsDir = currentDir.resolve(ProductBasePath);
        if (Files.exists(updatedJenkinsDir) && Files.isDirectory(updatedJenkinsDir)) {
            // Check for config.xml to confirm it's a Jenkins installation
            if (Files.exists(updatedJenkinsDir.resolve("config.xml"))) {
                // Add port detection logic here if needed
                return updatedJenkinsDir.toAbsolutePath().toString();
            }
            // Check for Dockerfile configurations
            // TODO: Implement docker file check
            if (Files.exists(updatedJenkinsDir.resolve("dockerfile"))) {
                return updatedJenkinsDir.toAbsolutePath().toString();
                // hasDocker = true;
            }

            // Check for Docker Compose configurations
            if (Files.exists(updatedJenkinsDir.resolve("docker-compose.yml")) ||
                    Files.exists(updatedJenkinsDir.resolve("docker-compose.yaml"))) {
                // TODO: Implement file contains text check
                // if (fileContainsText(file, "jenkins/jenkins") || fileContainsText(file,
                // "image: jenkins")) {
                System.out.println("Jenkins Compose config found");
                return updatedJenkinsDir.toAbsolutePath().toString();
                // hasCompose = true;
                // }
            }
        }

        return "null"; // Not found
    }

    public String defaultJenkinsPaths() throws IOException {
        for (String path : JENKINS_HOME_PATHS) {
            Path jenkinsHome = Paths.get(path);
            // Check if Jenkins Home exists
            if (Files.exists(jenkinsHome) && Files.isDirectory(jenkinsHome)) {
                // Check for config.xml to confirm it's a Jenkins installation
                if (Files.exists(jenkinsHome.resolve("config.xml"))) {
                    // Add port detection logic here if needed
                    return jenkinsHome.toAbsolutePath().toString();
                }
                // Check for Dockerfile configurations
                // TODO: Implement docker file check
                if (Files.exists(jenkinsHome.resolve("dockerfile"))) {
                    return jenkinsHome.toAbsolutePath().toString();
                    // hasDocker = true;
                }

                // Check for Docker Compose configurations
                if (Files.exists(jenkinsHome.resolve("docker-compose.yml")) ||
                        Files.exists(jenkinsHome.resolve("docker-compose.yaml"))) {
                    // TODO: Implement file contains text check
                    // if (fileContainsText(file, "jenkins/jenkins") || fileContainsText(file,
                    // "image: jenkins")) {
                    return jenkinsHome.toAbsolutePath().toString();
                    // hasCompose = true;
                    // }
                }
            }
        }
        return "null"; // Not found
    }
}
