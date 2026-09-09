package com.spring.api.hub.products.automation;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.File;
import java.util.List;
import java.util.Optional;

@Component
public class AutomationActions {
    ProcessBuilder processBuilder = new ProcessBuilder();

    // 1. Check if a process is running by its Process ID (PID)
    public boolean isPidRunning(Long pid) {
        Optional<ProcessHandle> ph = ProcessHandle.of(pid);
        return ph.isPresent() && ph.get().isAlive();
    }

    // 2. Check if a process is running by matching text in its command name
    public boolean isProcessRunning(String processName) {
        return ProcessHandle.allProcesses()
                .map(ph -> ph.info().command().orElse(""))
                .anyMatch(command -> command.contains(processName));
    }

    public Long executeProductProcess(List<String> command, String basePath, String action) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(new File(basePath)); // Base Path for Product Execution
            processBuilder.redirectErrorStream(true); // Merge error stream with output stream

            Process proc = processBuilder.start();
            Long pid = proc.pid();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
            }

            // System.out.println("PID: " + pid);
            int exitCode = proc.waitFor();
            System.out.println("Exited with code: " + exitCode);
            return pid;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return -1L;
    }

}
