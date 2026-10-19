package com.spring.api.hub.lifecycle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActuatorPathsTest {

    @Test
    void defaultsToTheStandardActuatorPrefix() {
        assertEquals("/actuator", ActuatorPaths.basePath(null));
        assertEquals("/actuator", ActuatorPaths.basePath(""));
        assertEquals("/actuator", ActuatorPaths.basePath("   "));
        assertEquals("/actuator", ActuatorPaths.basePath("/"));
    }

    @Test
    void stripsATrailingSlash() {
        assertEquals("/actuator", ActuatorPaths.basePath("/actuator/"));
        assertEquals("/manage", ActuatorPaths.basePath("/manage/"));
    }

    @Test
    void trimsSurroundingWhitespace() {
        assertEquals("/actuator", ActuatorPaths.basePath("  /actuator  "));
    }

    @Test
    void keepsACustomPrefix() {
        assertEquals("/manage", ActuatorPaths.basePath("/manage"));
        assertEquals("/internal/actuator", ActuatorPaths.basePath("/internal/actuator"));
    }

    @Test
    void matchesTheBasePathAndEverythingBeneathIt() {
        assertTrue(ActuatorPaths.isManagementPath("/actuator", "/actuator"));
        assertTrue(ActuatorPaths.isManagementPath("/actuator/health", "/actuator"));
        assertTrue(ActuatorPaths.isManagementPath("/actuator/health/readiness", "/actuator"));
    }

    @Test
    void doesNotMatchAPathThatMerelyStartsWithTheSameLetters() {
        assertFalse(ActuatorPaths.isManagementPath("/actuatorish", "/actuator"));
        assertFalse(ActuatorPaths.isManagementPath("/actuatorx/health", "/actuator"));
        assertFalse(ActuatorPaths.isManagementPath("/products/automation", "/actuator"));
        assertFalse(ActuatorPaths.isManagementPath("/actuator/health", "/manage"));
    }
}