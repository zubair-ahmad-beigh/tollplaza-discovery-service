package com.freightfox.tollplaza.controller;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Redirects the root path ("/") to the interactive Swagger UI documentation.
 */
@Controller
@Hidden
public class RootRedirectController {

    @GetMapping("/")
    public String redirectToSwaggerUi() {
        return "redirect:/swagger-ui.html";
    }
}
