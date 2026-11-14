package com.freightfox.tollplaza;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/**
 * Main entry point for the FreightFox Toll Plaza Service.
 *
 * Annotations:
 * - @SpringBootApplication: Declares this as a Spring Boot configuration class,
 *   enables auto-configuration, and triggers component scanning on com.freightfox.tollplaza.
 * - @EnableCaching: Enables Spring's annotation-driven cache management capability.
 */
@SpringBootApplication
@EnableCaching
public class TollPlazaApplication {

    public static void main(String[] args) {
        SpringApplication.run(TollPlazaApplication.class, args);
    }
}

