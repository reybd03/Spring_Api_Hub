package com.spring.api.automation.service;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Optional;

@Component
public class AutomationActions {

    public boolean isPidRunning(Long pid) {
        if (pid == null || pid <= 0) {
            return false;
        }
        Optional<ProcessHandle> ph = ProcessHandle.of(pid);
        return ph.isPresent() && ph.get().isAlive();
    }

    public boolean isProcessRunning(String processName) {
        if (processName == null || processName.isBlank()) {
            return false;
        }
        return ProcessHandle.allProcesses()
                .map(ph -> ph.info().command().orElse(""))
                .anyMatch(command -> command.contains(processName));
    }

    public boolean isComposeRunning(String basePath) {
        if (basePath == null || basePath.isBlank()) {
            return false;
        }
        File dir = new File(basePath);
        if (!dir.exists() || !dir.isDirectory()) {
            return false;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "compose", "ps", "--status", "running", "-q");
            pb.directory(dir);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            boolean hasRunningContainers = false;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.trim().isEmpty()) {
                        hasRunningContainers = true;
                    }
                }
            }
            int exitCode = proc.waitFor();
            return exitCode == 0 && hasRunningContainers;
        } catch (Exception e) {
            return false;
        }
    }

    public Long executeProductProcess(List<String> command, String basePath) {
        try {
            File dir = new File(basePath);
            if (!dir.exists() || !dir.isDirectory()) {
                System.out.println("Base path directory does not exist: " + basePath);
                return -1L;
            }
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(dir);
            processBuilder.redirectErrorStream(true);

            Process proc = processBuilder.start();
            Long pid = proc.pid();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
            }

            int exitCode = proc.waitFor();
            System.out.println("Exited with code: " + exitCode);
            if (exitCode != 0) {
                return -1L;
            }
            return pid;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return -1L;
    }
}