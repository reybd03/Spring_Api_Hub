package com.spring.api.hub.controllers;

import com.spring.api.hub.WebController;
import org.springframework.ui.Model;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.stereotype.Controller;

@Controller
@RequestMapping("/products/uml")
public class ProductUMLController {
    private final WebController webController;

    ProductUMLController(WebController webController) {
        this.webController = webController;
    }

    @GetMapping
    public String getUMLProduct(Model model) {
        model = webController.getEndpointsToModel(model);
        model.addAttribute("title", "UML");

        return "pages";
    }
}
