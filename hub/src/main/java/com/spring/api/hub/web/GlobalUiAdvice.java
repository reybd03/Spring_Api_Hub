package com.spring.api.hub.web;

import com.spring.api.hub.web.NavItem;

import java.util.Collections;
import java.util.List;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;

@ControllerAdvice(annotations = org.springframework.stereotype.Controller.class)
public class GlobalUiAdvice {

    private final NavigationService navigationService;

    public GlobalUiAdvice(NavigationService navigationService) {
        this.navigationService = navigationService;
    }

    @ModelAttribute
    public void addNavItemsToModel(Model model) {
        model.addAttribute("navItems", navigationService.getNavItems());
    }
}
