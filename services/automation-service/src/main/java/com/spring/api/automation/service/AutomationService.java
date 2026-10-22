package com.spring.api.automation.service;

import com.spring.api.automation.entity.AutomationEntity;
import com.spring.api.automation.repository.AutomationRepository;
import com.sun.management.OperatingSystemMXBean;

import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
public class AutomationService {

    private final OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    private final AutomationRepository automationRepository;
    private final Sinks.Many<String> metricsSink = Sinks.many().multicast().onBackpressureBuffer();

    public AutomationService(AutomationRepository automationRepository) {
        this.automationRepository = automationRepository;
    }

    public Mono<Map<String, Object>> getSystemMetrics() {
        return Mono.fromCallable(() -> {
            Runtime runtime = Runtime.getRuntime();
            Map<String, Object> metrics = new java.util.HashMap<>();

            long startTime = ManagementFactory.getRuntimeMXBean().getStartTime();
            long uptime = System.currentTimeMillis() - startTime;
            metrics.put("uptime", formatDuration(uptime));

            double systemCpuLoad = osBean.getCpuLoad();
            metrics.put("systemCpuLoad", String.format("%.2f%%", systemCpuLoad * 100));

            double processCpuLoad = osBean.getProcessCpuLoad();
            metrics.put("processCpuLoad", String.format("%.2f%%", processCpuLoad * 100));

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

            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;

            metrics.put("totalMemory", formatBytes(totalMemory));
            metrics.put("freeMemory", formatBytes(freeMemory));
            metrics.put("usedMemory", formatBytes(usedMemory));

            return metrics;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        return String.format("%.2f %s", bytes / Math.pow(1024, exp), units[exp]);
    }

    private String formatDuration(long millis) {
        long days = TimeUnit.MILLISECONDS.toDays(millis);
        long hours = TimeUnit.MILLISECONDS.toHours(millis) % 24;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;
        return String.format("%02d:%02d:%02d:%02d", days, hours, minutes, seconds);
    }

    public Flux<Map<String, Object>> getSystemMetricsStream() {
        return Flux.interval(Duration.ofSeconds(1))
                .flatMap(tick -> getSystemMetrics())
                .doOnNext(metrics -> metricsSink.tryEmitNext(metrics.toString()));
    }

    public AutomationEntity createAutomationEntity(AutomationEntity entity) {
        if (automationRepository.existsByProductName(entity.getProductName())) {
            throw new IllegalArgumentException("Product with name '" + entity.getProductName() + "' already exists");
        }
        return automationRepository.save(entity);
    }

    public Mono<AutomationEntity> createAutomationEntityMono(AutomationEntity entity) {
        return Mono.fromCallable(() -> createAutomationEntity(entity))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Optional<AutomationEntity> findByProductName(String productName) {
        return automationRepository.findByProductName(productName);
    }

    public Mono<Optional<AutomationEntity>> findByProductNameMono(String productName) {
        return Mono.fromCallable(() -> automationRepository.findByProductName(productName))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public List<AutomationEntity> findAll() {
        return automationRepository.findAll();
    }

    public Mono<List<AutomationEntity>> findAllMono() {
        return Mono.fromCallable(automationRepository::findAll)
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Optional<AutomationEntity> findById(Long id) {
        return automationRepository.findById(id);
    }

    public Mono<Optional<AutomationEntity>> findByIdMono(Long id) {
        return Mono.fromCallable(() -> automationRepository.findById(id))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public AutomationEntity updateAutomationEntity(AutomationEntity entity, String updateKey, String updateValue) {
        switch (updateKey) {
            case "productBasePath" -> entity.setProductBasePath(updateValue);
            case "productURL" -> entity.setProductURL(updateValue);
            case "productPort" -> entity.setProductPort(updateValue);
            case "productUserName" -> entity.setProductUserName(updateValue);
            case "productPassword" -> entity.setProductPassword(updateValue);
            case "productAPIKey" -> entity.setProductAPIKey(updateValue);
            case "productDiscoveryStatus" -> entity.setProductDiscoveryStatus(updateValue);
            case "productDiscovered" -> entity.setProductDiscovered(Boolean.parseBoolean(updateValue));
            case "productStatus" -> entity.setProductStatus(updateValue);
            case "productFunctions" -> entity.setProductFunctions(updateValue);
            case "productActions" -> entity.setProductActions(updateValue);
            default -> System.out.println("Unknown update key: " + updateKey);
        }
        return automationRepository.save(entity);
    }

    public Mono<AutomationEntity> updateAutomationEntityMono(AutomationEntity entity, String updateKey, String updateValue) {
        return Mono.fromCallable(() -> updateAutomationEntity(entity, updateKey, updateValue))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public AutomationEntity save(AutomationEntity entity) {
        return automationRepository.save(entity);
    }

    public Mono<AutomationEntity> saveMono(AutomationEntity entity) {
        return Mono.fromCallable(() -> automationRepository.save(entity))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public void deleteById(Long id) {
        automationRepository.deleteById(id);
    }

    public Mono<Void> deleteByIdMono(Long id) {
        return Mono.fromRunnable(() -> automationRepository.deleteById(id))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    public int deleteDuplicates() {
        return automationRepository.deleteDuplicatesByProductName();
    }
}