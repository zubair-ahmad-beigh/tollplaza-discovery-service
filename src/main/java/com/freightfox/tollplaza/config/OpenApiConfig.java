package com.freightfox.tollplaza.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("FreightFox Toll Plaza Locator API")
                        .version("1.0.0")
                        .description("""
                            Production-quality Spring Boot REST API that resolves Indian postal codes (pincodes),
                            computes real highway driving routes, and identifies and sequences all highway toll plazas
                            encountered along the journey.
                            """)
                        .contact(new Contact()
                                .name("FreightFox Backend Assignment")
                                .email("backend@freightfox.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://springdoc.org")));
    }
}

