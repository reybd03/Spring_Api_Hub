package com.spring.api.hub.web;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping;

@Service
public class NavigationService {

    /**
     * Spring Cloud Gateway contributes its own RequestMappingHandlerMapping, and the actuator
     * web endpoints contribute a third, so this must name the MVC/WebFlux mapping explicitly
     * rather than resolve by type.
     */
    public static final String HANDLER_MAPPING_BEAN = "requestMappingHandlerMapping";

    private final List<NavItem> navItems;

    // Injecting the WebFlux RequestMappingHandlerMapping
    public NavigationService(
            @Qualifier(HANDLER_MAPPING_BEAN) RequestMappingHandlerMapping handlerMapping) {
        this.navItems = handlerMapping.getHandlerMethods().entrySet().stream()
                // 1. Filter endpoints that accept standard GET requests
                .filter(entry -> entry.getKey().getMethodsCondition().getMethods().contains(RequestMethod.GET))
                // 2. Map them out to paths
                .flatMap(entry -> entry.getKey().getPatternsCondition().getPatterns().stream())
                // 3. Clean up patterns (skip APIs, static elements, and wildcards)
                .map(pathPattern -> pathPattern.getPatternString())
                .filter(path -> !path.startsWith("/api") && !path.contains("{") && !path.equals("/error")
                        && !path.contains("stream"))
                // 3b. Management endpoints are infrastructure, not pages a user should navigate to.
                .filter(path -> !path.equals("/actuator") && !path.startsWith("/actuator/"))
                // 4. Map string paths to your NavItem record
                .map(path -> {
                    String title = path.equals("/") ? "Home" : capitalize(path.substring(1));
                    return new NavItem(title, path);
                })
                .distinct()
                .toList();
    }

    public List<NavItem> getNavItems() {
        return navItems;
    }

    // Helper method to turn a path like "/about-us" into a display name like
    // "About-us"
    private static String capitalize(String text) {
        if (text == null || text.isEmpty())
            return text;
        return text.substring(0, 1).toUpperCase() + text.substring(1);
    }
}
