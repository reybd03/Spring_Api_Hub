package com.spring.api.hub.web;

import com.spring.api.hub.web.NavItem;

import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping;

@Service
public class NavigationService {
    private final List<NavItem> navItems;

    // Injecting the WebFlux RequestMappingHandlerMapping
    public NavigationService(RequestMappingHandlerMapping handlerMapping) {
        this.navItems = handlerMapping.getHandlerMethods().entrySet().stream()
                // 1. Filter endpoints that accept standard GET requests
                .filter(entry -> entry.getKey().getMethodsCondition().getMethods().contains(RequestMethod.GET))
                // 2. Map them out to paths
                .flatMap(entry -> entry.getKey().getPatternsCondition().getPatterns().stream())
                // 3. Clean up patterns (skip APIs, static elements, and wildcards)
                .map(pathPattern -> pathPattern.getPatternString())
                .filter(path -> !path.startsWith("/api") && !path.contains("{") && !path.equals("/error"))
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
