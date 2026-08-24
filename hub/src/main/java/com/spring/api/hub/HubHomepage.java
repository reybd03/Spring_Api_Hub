package com.spring.api.hub;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;

@RestController
public class HubHomepage {

    @GetMapping("/")
    public String hubHomepage() {
        return "Home";
    }
}
