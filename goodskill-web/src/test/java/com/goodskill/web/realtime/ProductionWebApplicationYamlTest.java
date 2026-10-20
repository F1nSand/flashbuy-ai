package com.goodskill.web.realtime;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionWebApplicationYamlTest {

    @Test
    void applicationConfigurationContainsParsableProfileDocuments() throws Exception {
        var documents = new YamlPropertySourceLoader().load("application",
                new ClassPathResource("application.yml"));

        assertTrue(documents.size() >= 3);
        assertTrue(documents.stream().anyMatch(document -> "production"
                .equals(document.getProperty("spring.config.activate.on-profile"))));
    }
}
