package com.spring.api.hub.controllers;

import com.spring.api.hub.admin.AdminService;
import com.spring.api.hub.lifecycle.GracefulShutdownCoordinator;
import com.spring.api.hub.lifecycle.InFlightRequestTracker;
import com.spring.api.hub.lifecycle.ShutdownProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminControllerTest {

    private AdminService adminService;
    private JdbcTemplate jdbcTemplate;
    private GracefulShutdownCoordinator shutdownCoordinator;
    private InFlightRequestTracker requestTracker;
    private ShutdownProperties shutdownProperties;
    private AdminController adminController;

    @BeforeEach
    void setUp() {
        adminService = new AdminService();
        jdbcTemplate = mock(JdbcTemplate.class);
        shutdownCoordinator = mock(GracefulShutdownCoordinator.class);
        requestTracker = mock(InFlightRequestTracker.class);
        shutdownProperties = new ShutdownProperties(Duration.ofSeconds(20), "test-token");
        adminController = new AdminController(adminService, jdbcTemplate, shutdownCoordinator, requestTracker,
                shutdownProperties, "8082", "8082", "/actuator");
    }

    @Test
    void createTableWithSqlInjectionShouldSetErrorAndNotExecuteSql() {
        Model model = new ConcurrentModel();
        String view = adminController.createTable("users; DROP TABLE users; --", model);

        assertEquals("admin", view);
        assertTrue(model.containsAttribute("error"));
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void queryTableWithSqlInjectionShouldSetErrorAndNotExecuteSql() {
        Model model = new ConcurrentModel();
        String view = adminController.queryTable("users' OR '1'='1", model);

        assertEquals("admin", view);
        assertTrue(model.containsAttribute("error"));
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void dropTableWithSqlInjectionShouldSetErrorAndNotExecuteSql() {
        Model model = new ConcurrentModel();
        String view = adminController.dropTable("users; DROP TABLE products; --", model);

        assertEquals("admin", view);
        assertTrue(model.containsAttribute("error"));
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void createTableValidShouldExecuteSql() {
        Model model = new ConcurrentModel();
        String view = adminController.createTable("valid_table_name", model);

        assertEquals("admin", view);
        assertTrue(model.containsAttribute("message"));
        assertFalse(model.containsAttribute("error"));
        verify(jdbcTemplate, times(1)).execute(anyString());
    }

    @Test
    void shutdownWithoutTokenReturnsServiceUnavailableAndDoesNotClaimSuccess() {
        ShutdownProperties noToken = new ShutdownProperties(Duration.ofSeconds(20), "");
        AdminController controller = new AdminController(adminService, jdbcTemplate, shutdownCoordinator,
                requestTracker, noToken, "8082", "8082", "/actuator");

        ResponseEntity<Map<String, Object>> response = controller.initiateShutdown();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals(false, response.getBody().get("success"));
    }

    @Test
    void shutdownStatusReflectsDrainingStateAndInFlightCount() {
        when(shutdownCoordinator.isDraining()).thenReturn(true);
        when(shutdownCoordinator.isComplete()).thenReturn(false);
        when(requestTracker.isDraining()).thenReturn(true);
        when(requestTracker.getInFlightCount()).thenReturn(3);

        Map<String, Object> status = adminController.getShutdownStatus();

        assertEquals(true, status.get("draining"));
        assertEquals(false, status.get("complete"));
        assertEquals(3, status.get("inFlightRequests"));
        assertEquals(25, status.get("progressPercent"));
        assertEquals(true, status.get("tokenConfigured"));
    }

    @Test
    void shutdownStatusReportsCompletion() {
        when(shutdownCoordinator.isComplete()).thenReturn(true);
        when(requestTracker.getInFlightCount()).thenReturn(0);

        Map<String, Object> status = adminController.getShutdownStatus();

        assertEquals(false, status.get("draining"));
        assertEquals(true, status.get("complete"));
        assertEquals(100, status.get("progressPercent"));
    }
}
