package com.spring.api.hub.products.automation;

import com.spring.api.hub.products.automation.AutomationEntity;
import com.spring.api.hub.products.automation.AutomationRepository;

import java.util.Map;
import java.time.Duration;
import java.lang.management.ManagementFactory;

import org.springframework.http.codec.ServerSentEvent;

import com.sun.management.OperatingSystemMXBean;
import com.fasterxml.jackson.databind.ObjectMapper;
// import com.fasterxml.jackson.databind.SerializationFeature;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import org.springframework.stereotype.Service;

@Service
public class AutomationService {

    OperatingSystemMXBean osBean = (OperatingSystemMXBean) java.lang.management.ManagementFactory
            .getOperatingSystemMXBean();

    private final AutomationRepository automationRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    AutomationService(AutomationRepository automationRepository) {
        this.automationRepository = automationRepository;
    }

    public Mono<Map<String, Object>> getSystemMetrics() {
        // Create a CompletableFuture to bridge reactive and blocking APIs
        return Mono.fromFuture(java.util.concurrent.CompletableFuture.supplyAsync(() -> {

            Runtime runtime = Runtime.getRuntime();

            Map<String, Object> metrics = new java.util.HashMap<>();

            // System CPU Usage
            double systemCpuLoad = osBean.getCpuLoad();
            metrics.put("systemCpuLoad", String.format("%.2f%%", systemCpuLoad * 100));

            // Process CPU Usage
            double processCpuLoad = osBean.getProcessCpuLoad();
            metrics.put("processCpuLoad", String.format("%.2f%%", processCpuLoad * 100));

            // Swap Memory Metrics (if available)
            long totalSwapSpaceSize = osBean.getTotalSwapSpaceSize();
            if (totalSwapSpaceSize > 0) {
                long freeSwapSpaceSize = osBean.getFreeSwapSpaceSize();
                long usedSwapSpaceSize = totalSwapSpaceSize - freeSwapSpaceSize;

                metrics.put("totalSwapSpaceSize", formatBytes(totalSwapSpaceSize));
                metrics.put("freeSwapSpaceSize", formatBytes(freeSwapSpaceSize));
                metrics.put("usedSwapSpaceSize", formatBytes(usedSwapSpaceSize));
            } else {
                metrics.put("totalSwapSpaceSize", "N/A");
                metrics.put("freeSwapSpaceSize", "N/A");
                metrics.put("usedSwapSpaceSize", "N/A");
            }

            // Process Memory Metrics
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;

            metrics.put("totalMemory", formatBytes(totalMemory));
            metrics.put("freeMemory", formatBytes(freeMemory));
            metrics.put("usedMemory", formatBytes(usedMemory));

            return metrics;
        }));
    }

    /**
     * Helper method to format byte values to human-readable format
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String[] units = { "B", "KB", "MB", "GB", "TB" };
        return String.format("%.2f %s", bytes / Math.pow(1024, exp), units[exp]);
    }

    /**
     * Helper method to create a Server-Sent Event stream
     * 
     * @param monoMap   - Mono containing the data to be sent
     * @param eventName - Name of the event
     * @return Flux of Server-Sent Events
     */
    public Flux<ServerSentEvent<Map<String, Object>>> fluxBoilerplate(Mono<Map<String, Object>> monoMap,
            String eventName) {
        return Flux.interval(Duration.ofSeconds(1)) // Poll metrics every second
                .flatMap(tick -> monoMap)
                .map(data -> ServerSentEvent.<Map<String, Object>>builder()
                        .id(String.valueOf(System.currentTimeMillis()))
                        .event(eventName)
                        .data(data)
                        .build());
    }

    public AutomationEntity createAutomationEntity(AutomationEntity product) {
        AutomationEntity automationEntity = new AutomationEntity();
        automationEntity.setProductDiscoveryStatus(product.getProductDiscoveryStatus());
        automationEntity.setProductDiscovered(product.getProductDiscovered());
        automationEntity.setProductBasePath(product.getProductBasePath());
        automationEntity.setProductUserName(product.getProductUserName());
        automationEntity.setProductPassword(product.getProductPassword());
        automationEntity.setProductAPIKey(product.getProductAPIKey());
        automationEntity.setProductURL(product.getProductURL());
        automationEntity.setProductName(product.getProductName());
        automationEntity.setProductPort(product.getProductPort());
        automationEntity.setProductStatus(product.getProductStatus());
        automationEntity.setProductFunctions(product.getProductFunctions());
        automationEntity.setProductActions(product.getProductActions());

        automationEntity = automationRepository.save(automationEntity);
        return automationEntity;
    }

    public void prettyPrintEntity(AutomationEntity automationEntity) {
        try {
            String prettyJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(automationEntity);
            System.out.println(prettyJson);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
