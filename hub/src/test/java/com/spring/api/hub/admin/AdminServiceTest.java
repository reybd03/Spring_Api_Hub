package com.spring.api.hub.admin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdminServiceTest {

    private final AdminService adminService = new AdminService();

    @ParameterizedTest
    @ValueSource(strings = {"users", "test_table", "table123", "Product_2026"})
    void validTableNamesShouldPass(String validName) {
        assertDoesNotThrow(() -> adminService.validateTableName(validName));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "users; DROP TABLE products; --",
            "users WHERE 1=1",
            "users' OR '1'='1",
            "table name with spaces",
            "table-with-dashes",
            "table$name",
            "",
            "   "
    })
    void invalidTableNamesShouldThrowException(String invalidName) {
        assertThrows(IllegalArgumentException.class, () -> adminService.validateTableName(invalidName));
    }

    @Test
    void nullTableNameShouldThrowException() {
        assertThrows(IllegalArgumentException.class, () -> adminService.validateTableName(null));
    }
}
