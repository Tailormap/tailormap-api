/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import nl.b3p.tailormap.api.HSQLDBTestProfileJPAConfiguration;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = {HSQLDBTestProfileJPAConfiguration.class, AppController.class})
@AutoConfigureMockMvc
@EnableAutoConfiguration
@ActiveProfiles("test")
class AppControllerIntegrationTest {
    @Autowired private MockMvc mockMvc;

    private String getApiVersionFromPom() {
        String apiVersion = System.getenv("API_VERSION");
        assumeFalse(null == apiVersion, "API version unknown, should be set in environment");
        return apiVersion;
    }

    @Test
    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
    void should_find_default_when_no_arguments() throws Exception {
        mockMvc.perform(get("/app"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.apiVersion").value(getApiVersionFromPom()))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("test"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.lang").value("nl_NL"))
                .andExpect(jsonPath("$.title").value("test title"));
    }

    @Test
    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
    void should_find_default_when_nonexistent_id() throws Exception {
        mockMvc.perform(get("/app?appid=100"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.apiVersion").value(getApiVersionFromPom()))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("test"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.lang").value("nl_NL"))
                .andExpect(jsonPath("$.title").value("test title"));
    }

    @Test
    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
    void should_find_default_when_nonexistent_name_and_version() throws Exception {
        mockMvc.perform(get("/app?name=testing123&version=100"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.apiVersion").value(getApiVersionFromPom()))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("test"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.lang").value("nl_NL"))
                .andExpect(jsonPath("$.title").value("test title"));
    }

    @Test
    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
    void should_find_by_id() throws Exception {
        mockMvc.perform(get("/app?appid=1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.apiVersion").value(getApiVersionFromPom()))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("test"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.lang").value("nl_NL"))
                .andExpect(jsonPath("$.title").value("test title"));
    }

    @Test
    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
    void should_find_by_name() throws Exception {
        mockMvc.perform(get("/app?name=test"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.apiVersion").value(getApiVersionFromPom()))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("test"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.lang").value("nl_NL"))
                .andExpect(jsonPath("$.title").value("test title"));
    }

    @Test
    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
    void should_find_by_name_and_version() throws Exception {
        mockMvc.perform(get("/app?name=test&version=1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.apiVersion").value(getApiVersionFromPom()))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("test"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.lang").value("nl_NL"))
                .andExpect(jsonPath("$.title").value("test title"));
    }

    @Test
    @Disabled("TODO setup test data for this (delete default application from metadata table)")
    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
    void should_error_when_no_default_application_and_using_nonexistent_id() throws Exception {
        // TODO setup test data for this (delete default application from metadata table)
        mockMvc.perform(get("/app?appid=666"))
                .andExpect(status().is5xxServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }
}
