package com.spring.api.hub.controllers;

import com.spring.api.hub.WebController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProductUMLControllerTest {

    private WebController webController;
    private ProductUMLController controller;

    @BeforeEach
    void setUp() {
        webController = mock(WebController.class);
        when(webController.getEndpointsToModel(any(Model.class))).thenAnswer(inv -> inv.getArgument(0));
        controller = new ProductUMLController(webController);
    }

    @Test
    void testGetUMLProduct() {
        Model model = new ConcurrentModel();
        String view = controller.getUMLProduct(model);

        assertEquals("pages", view);
        assertEquals("UML", model.getAttribute("title"));
        verify(webController).getEndpointsToModel(model);
    }
}
