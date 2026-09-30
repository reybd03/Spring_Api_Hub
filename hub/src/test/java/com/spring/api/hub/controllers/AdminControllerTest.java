package com.spring.api.hub.controllers;

import com.spring.api.hub.admin.AdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminControllerTest {

    private AdminService adminService;
    private JdbcTemplate jdbcTemplate;
    private AdminController adminController;

    @BeforeEach
    void setUp() {
        adminService = new AdminService();
        jdbcTemplate = mock(JdbcTemplate.class);
        adminController = new AdminController(adminService, jdbcTemplate);
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
}
