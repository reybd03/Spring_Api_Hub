package com.spring.api.common.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SecurityConstantsTest {

    @Test
    void testConstants() {
        assertEquals("Authorization", SecurityConstants.AUTH_HEADER);
        assertEquals("Bearer ", SecurityConstants.BEARER_PREFIX);
    }
}
