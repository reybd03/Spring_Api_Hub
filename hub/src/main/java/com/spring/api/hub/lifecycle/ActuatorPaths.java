package com.spring.api.hub.lifecycle;

/**
 * Resolves the actuator base path so the filters and their tests all agree on where the
 * management endpoints live, without duplicating the normalisation rules.
 */
final class ActuatorPaths {

    static final String DEFAULT_BASE_PATH = "/actuator";

    private ActuatorPaths() {
    }

    /**
     * @param configured value of {@code management.endpoints.web.base-path}
     * @return the prefix without a trailing slash, falling back to {@code /actuator}
     */
    static String basePath(String configured) {
        if (configured == null || configured.isBlank()) {
            return DEFAULT_BASE_PATH;
        }
        String trimmed = configured.trim();
        if (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.isEmpty() ? DEFAULT_BASE_PATH : trimmed;
    }

    /**
     * Whether the given application-relative path belongs to the management endpoints, which
     * stay reachable and uncounted while an instance drains.
     */
    static boolean isManagementPath(String path, String basePath) {
        return path.equals(basePath) || path.startsWith(basePath + "/");
    }
}