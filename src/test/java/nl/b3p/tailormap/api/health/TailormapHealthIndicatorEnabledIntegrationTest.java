/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.health;

import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import nl.b3p.tailormap.api.HSQLDBTestProfileJPAConfiguration;
import nl.b3p.tailormap.api.controller.VersionController;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.actuate.metrics.AutoConfigureMetrics;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
        classes = {
            HSQLDBTestProfileJPAConfiguration.class,
            TailormapHealthIndicator.class,
            VersionController.class
        })
@AutoConfigureMockMvc
@EnableAutoConfiguration
@TestPropertySource(properties = {"management.health.tailormap.enabled=true"})
@ActiveProfiles("test")
@AutoConfigureMetrics
class TailormapHealthIndicatorEnabledIntegrationTest {
    @Autowired private MockMvc mockMvc;

    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
    @Test
    void when_enabled_health_should_have_status_and_response_data() throws Exception {
        String projectVersion = System.getProperty("project.version");
        assumeFalse(
                null == projectVersion,
                "Project version unknown, should be set as system property");
        String databaseVersion = System.getenv("DATABASE_VERSION");
        assumeFalse(
                null == databaseVersion,
                "Database version unknown, should be set in system environment");
        String apiVersion = System.getenv("API_VERSION");
        assumeFalse(null == apiVersion, "API version unknown, should be set in system environment");

        mockMvc.perform(get("/actuator/health/tailormap"))
                .andExpect(status().isOk())
                .andExpect(
                        content()
                                .contentType(
                                        MediaType.parseMediaType(
                                                "application/vnd.spring-boot.actuator.v3+json")))
                .andExpect(jsonPath("$.details.version").value(projectVersion))
                .andExpect(jsonPath("$.details.apiVersion").value(apiVersion))
                .andExpect(jsonPath("$.details.databaseversion").value(databaseVersion))
                .andExpect(jsonPath("$.details.buildDate").isNotEmpty());
    }
}
