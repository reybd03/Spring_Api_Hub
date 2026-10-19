package com.spring.api.common.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApiResponseTest {

    @Test
    void testOkResponse() {
        ApiResponse<String> response = ApiResponse.ok("test-data");
        assertTrue(response.isSuccess());
        assertEquals("test-data", response.getData());
        assertNotNull(response.getTimestamp());
    }

    @Test
    void testErrorResponse() {
        ApiResponse<Void> response = ApiResponse.error("Something went wrong");
        assertFalse(response.isSuccess());
        assertEquals("Something went wrong", response.getMessage());
        assertNull(response.getData());
    }
}
