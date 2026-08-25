package com.spring.api.hub.controllers;

import com.spring.api.hub.WebController;
import org.springframework.ui.Model;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.stereotype.Controller;

@Controller
@RequestMapping("/products/automation")
public class ProductAutomationController {
    private final WebController webController;

    ProductAutomationController(WebController webController) {
        this.webController = webController;
    }

    @GetMapping
    public String getAutomationProduct(Model model) {
        model = webController.getEndpointsToModel(model);
        model.addAttribute("title", "Automation");

        return "pages";
    }
}
