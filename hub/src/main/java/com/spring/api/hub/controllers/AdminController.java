package com.spring.api.hub.controllers;

/**
 * TODO: Convert most of this into a product
 */

import com.spring.api.hub.admin.AdminService;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private AdminService adminService;
    private JdbcTemplate jdbcTemplate;

    public AdminController(AdminService adminService, JdbcTemplate jdbcTemplate) {
        this.adminService = adminService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public String getMethodName(Model model) {
        model.addAttribute("title", "Admin Page");
        return "admin";
    }

    // Handle Table Creation
    @PostMapping("/create-table")
    public String createTable(@RequestParam() String tableName, Model model) {
        try {
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
        System.out.println(tableName);
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM " + tableName);
            model.addAttribute("rows", rows);
            model.addAttribute("tableName", tableName);
            model.addAttribute("tableResults", "Results for Table: " + tableName);
            // return rows;
        } catch (Exception e) {
            model.addAttribute("error", "Error querying table " + tableName + ": " + e.getMessage());
            System.out.println(e.getMessage());
            // return ResponseEntity.badRequest().body("Invalid user ID provided");
        }
        return "admin";
    }

    // Handle Table Dropping
    @PostMapping("/drop-table")
    public String dropTable(@RequestParam("tableName") String tableName, Model model) {
        try {
            String sql = "DROP TABLE " + tableName;
            jdbcTemplate.execute(sql);
            model.addAttribute("message", "Table '" + tableName + "' dropped successfully.");
        } catch (Exception e) {
            model.addAttribute("error", "Error dropping table: " + e.getMessage());
        }
        return "admin";
    }
}
