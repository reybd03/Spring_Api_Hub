package com.spring.api.hub.controllers;

import com.spring.api.hub.WebController;
import org.springframework.ui.Model;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.stereotype.Controller;

@Controller
@RequestMapping("/products/mainframe")
public class ProductMainframeController {
    private final WebController webController;

    ProductMainframeController(WebController webController) {
        this.webController = webController;
    }

    @GetMapping
    public String getMainframeProduct(Model model) {
        model = webController.getEndpointsToModel(model);
        model.addAttribute("title", "Mainframe");

        return "pages";
    }
}
