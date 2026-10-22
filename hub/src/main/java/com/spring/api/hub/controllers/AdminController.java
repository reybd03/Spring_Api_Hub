package com.spring.api.hub.controllers;

/**
 * TODO: Convert most of this into a product
 */

import com.spring.api.hub.admin.AdminService;
import com.spring.api.hub.lifecycle.GracefulShutdownCoordinator;
import com.spring.api.hub.lifecycle.InFlightRequestTracker;
import com.spring.api.hub.lifecycle.ShutdownProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final AdminService adminService;
    private final JdbcTemplate jdbcTemplate;
    private final GracefulShutdownCoordinator shutdownCoordinator;
    private final InFlightRequestTracker requestTracker;
    private final ShutdownProperties shutdownProperties;
    private final RestTemplate restTemplate;
    private final String managementPort;
    private final String actuatorBasePath;

    public AdminController(AdminService adminService, JdbcTemplate jdbcTemplate,
                           GracefulShutdownCoordinator shutdownCoordinator,
                           InFlightRequestTracker requestTracker,
                           ShutdownProperties shutdownProperties,
                           @Value("${server.port:8082}") String serverPort,
                           @Value("${management.server.port:${server.port:8082}}") String managementPort,
                           @Value("${management.endpoints.web.base-path:/actuator}") String actuatorBasePath) {
        this.adminService = adminService;
        this.jdbcTemplate = jdbcTemplate;
        this.shutdownCoordinator = shutdownCoordinator;
        this.requestTracker = requestTracker;
        this.shutdownProperties = shutdownProperties;
        this.restTemplate = new RestTemplate();
        this.managementPort = managementPort;
        this.actuatorBasePath = normaliseBasePath(actuatorBasePath);
    }

    private static String normaliseBasePath(String configured) {
        if (configured == null || configured.isBlank()) {
            return "/actuator";
        }
        String trimmed = configured.trim();
        while (trimmed.endsWith("/") && trimmed.length() > 1) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    @GetMapping
    public String getMethodName(Model model) {
        model.addAttribute("title", "Admin Page");
        // Pass drain timeout to template for display
        model.addAttribute("drainTimeoutSeconds", shutdownProperties.drainTimeout().getSeconds());
        return "admin";
    }

    // Handle Table Creation
    @PostMapping("/create-table")
    public String createTable(@RequestParam() String tableName, Model model) {
        try {
            adminService.validateTableName(tableName);
            String sql = "CREATE TABLE " + tableName
                    + " (id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(255), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)";
            jdbcTemplate.execute(sql);
            model.addAttribute("message",
                    "Table '" + tableName + "' created successfully with columns: id, name, created_at!");
        } catch (Exception e) {
            model.addAttribute("error", "Error creating table: " + e.getMessage());
        }
        return "admin";
    }

    // Handle Table Querying
    @PostMapping("/query-table")
    public String queryTable(@RequestParam("tableName") String tableName, Model model) {
        try {
            adminService.validateTableName(tableName);
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM " + tableName);
            model.addAttribute("rows", rows);
            model.addAttribute("tableName", tableName);
            model.addAttribute("tableResults", "Results for Table: " + tableName);
        } catch (Exception e) {
            model.addAttribute("error", "Error querying table " + tableName + ": " + e.getMessage());
        }
        return "admin";
    }

    // Handle Table Dropping
    @PostMapping("/drop-table")
    public String dropTable(@RequestParam("tableName") String tableName, Model model) {
        try {
            adminService.dropTableIfExists(tableName);
            model.addAttribute("message", "Table '" + tableName + "' dropped successfully.");
        } catch (Exception e) {
            model.addAttribute("error", "Error dropping table: " + e.getMessage());
        }
        return "admin";
    }

    /**
     * Initiate graceful platform shutdown via the actuator endpoint.
     * This endpoint is called from the admin UI with double-confirmation.
     */
    @PostMapping("/shutdown")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> initiateShutdown() {
        String token = shutdownProperties.shutdownToken();
        if (token == null || token.isBlank()) {
            log.warn("Shutdown attempted but no shutdown token is configured");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "success", false,
                    "message", "Shutdown token not configured. Set platform.shutdown.shutdown-token to enable shutdown."
            ));
        }

        try {
            String shutdownUrl = "http://localhost:" + managementPort + actuatorBasePath + "/shutdown";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set(ShutdownProperties.TOKEN_HEADER, token);

            HttpEntity<String> request = new HttpEntity<>("{}", headers);

            log.info("Initiating graceful shutdown via {}", shutdownUrl);
            ResponseEntity<String> response = restTemplate.exchange(
                    shutdownUrl, HttpMethod.POST, request, String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Shutdown initiated successfully");
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "Graceful shutdown initiated. Traffic draining in progress."
                ));
            }

            log.error("Shutdown endpoint returned: {}", response.getStatusCode());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "success", false,
                    "message", "Shutdown endpoint returned: " + response.getStatusCode()
            ));

        } catch (Exception e) {
            log.error("Failed to initiate shutdown", e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "success", false,
                    "message", "Failed to initiate shutdown: " + e.getMessage()
            ));
        }
    }

    /**
     * Get current shutdown status for polling from the UI.
     */
    @GetMapping("/shutdown/status")
    @ResponseBody
    public Map<String, Object> getShutdownStatus() {
        boolean draining = shutdownCoordinator.isDraining() || requestTracker.isDraining();
        boolean complete = shutdownCoordinator.isComplete();

        return Map.of(
                "draining", draining,
                "complete", complete,
                "inFlightRequests", requestTracker.getInFlightCount(),
                "drainTimeoutMs", shutdownProperties.drainTimeout().toMillis(),
                "progressPercent", progressPercent(draining, complete, requestTracker.getInFlightCount()),
                "tokenConfigured", shutdownProperties.hasToken(),
                "timestamp", Instant.now().toString()
        );
    }

    private static int progressPercent(boolean draining, boolean complete, int inFlight) {
        if (complete) return 100;
        if (!draining) return 0;
        if (inFlight == 0) return 95;
        return Math.min(90, 10 + (inFlight * 5));
    }
}
