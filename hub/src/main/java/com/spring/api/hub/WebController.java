package com.spring.api.hub;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.ArrayList;
import java.util.stream.Stream;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping;
import reactor.core.publisher.Mono;

@Controller
public class WebController {
    private final ApplicationContext applicationContext;
    private static final Logger log = LoggerFactory.getLogger(WebController.class);

    public WebController(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @GetMapping("/")
    public Mono<String> index(Model model) {
        // model = getEndpointsToModel(model);

        return Mono.just("index");
    }

    public Model getEndpointsToModel(Model model) {
        try {
            var endpoints = RequestMappingInfoToStream().toList();
            var endpoints_text = RequestMappingInfoToStream().map(str -> {
                if (str.equals("/"))
                    return "Home";
                String capitalized = StringUtils.capitalize(str.substring(str.lastIndexOf("/") + 1));
                return capitalized;
            }).toList();

            List<String> endpointsList = new ArrayList<>();

            for (int i = 0; i < endpoints.size(); i++) {
                endpointsList.add(endpoints.get(i) + endpoints_text.get(i));
            }
            model.addAttribute("endpoints", endpoints);
            model.addAttribute("endpoints_text", endpoints_text);

            return model;
        } catch (Exception e) {
            e.printStackTrace();

            int errorCode = 500;
            String errorMessage = "Error getting endpoints";

            List<String> errorList = new ArrayList<String>();

            errorList.add(String.valueOf(errorCode));
            errorList.add(errorMessage);

            model.addAttribute("endpoints", errorList);

            return model;
        }
    }

    public Stream<String> RequestMappingInfoToStream() {
        RequestMappingHandlerMapping handlerMapping = (RequestMappingHandlerMapping) applicationContext
                .getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);

        return handlerMapping.getHandlerMethods().keySet().stream()
                .map(requestMappingInfo -> requestMappingInfo.getPatternsCondition().getDirectPaths().stream()
                        .findFirst()
                        .orElse(""));
    }
}
