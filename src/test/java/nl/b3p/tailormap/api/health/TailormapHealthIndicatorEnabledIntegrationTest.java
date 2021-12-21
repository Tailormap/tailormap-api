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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = {HSQLDBTestProfileJPAConfiguration.class})
@AutoConfigureMockMvc
@TestPropertySource(properties = "management.health.tailormap.enabled=true")
@ActiveProfiles("test")
class TailormapHealthIndicatorEnabledIntegrationTest {

    private static String projectVersion;
    private static String apiVersion;
    private static String databaseVersion;
    @Autowired private MockMvc mockMvc;

    @BeforeAll
    static void getVersionFromPom() {
        // set through maven surefire/failsafe plugin from pom
        projectVersion = System.getProperty("project.version");
        assumeFalse(
                null == projectVersion,
                "Project version unknown, should be set in system environment");
        apiVersion = System.getProperty("api.version");
        assumeFalse(null == apiVersion, "API version unknown, should be set in system environment");
        databaseVersion = System.getProperty("database.version");
        assumeFalse(
                null == databaseVersion,
                "Database version unknown, should be set in system environment");
    }

    @Test
    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
    void when_enabled_health_should_have_status_and_response_data() throws Exception {
        mockMvc.perform(get("/api/actuator/health/tailormap"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.details.version").value(projectVersion))
                .andExpect(jsonPath("$.details.apiVersion").value(apiVersion))
                .andExpect(jsonPath("$.details.databaseversion").value(databaseVersion));
    }
}
