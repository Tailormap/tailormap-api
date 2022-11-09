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
import nl.b3p.tailormap.api.repository.ApplicationRepository;
import nl.b3p.tailormap.api.repository.MetadataRepository;
import nl.b3p.tailormap.api.security.AuthorizationService;
import nl.b3p.tailormap.api.security.SecurityConfig;
import nl.tailormap.viewer.config.metadata.Metadata;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
        classes = {
            HSQLDBTestProfileJPAConfiguration.class,
            AppController.class,
            SecurityConfig.class,
            AuthorizationService.class
        })
@AutoConfigureMockMvc
@EnableAutoConfiguration
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AppControllerIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired MetadataRepository metadataRepository;
    @Autowired ApplicationRepository applicationRepository;

    private String getApiVersionFromPom() {
        String apiVersion = System.getenv("API_VERSION");
        assumeFalse(null == apiVersion, "API version unknown, should be set in environment");
        return apiVersion;
    }

    @Test
    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
    void should_find_default_when_no_arguments() throws Exception {
        mockMvc.perform(get("/app").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.apiVersion").value(getApiVersionFromPom()))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("test"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.lang").value("nl_NL"))
                .andExpect(jsonPath("$.title").value("test title"))
                .andExpect(jsonPath("$.components").isArray())
                .andExpect(jsonPath("$.components[0].type").value("measure"));
    }

    @Test
    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
    void should_find_default_when_nonexistent_id() throws Exception {
        mockMvc.perform(get("/app").param("appId", "100").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.apiVersion").value(getApiVersionFromPom()))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("test"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.lang").value("nl_NL"))
                .andExpect(jsonPath("$.title").value("test title"))
                .andExpect(jsonPath("$.components").isArray())
                .andExpect(jsonPath("$.components[0].type").value("measure"));
    }

    @Test
    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
    void should_find_default_when_nonexistent_name_and_version() throws Exception {
        mockMvc.perform(
                        get("/app")
                                .param("name", "testing123")
                                .param("version", "100")
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.apiVersion").value(getApiVersionFromPom()))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("test"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.lang").value("nl_NL"))
                .andExpect(jsonPath("$.title").value("test title"))
                .andExpect(jsonPath("$.components").isArray())
                .andExpect(jsonPath("$.components[0].type").value("measure"));
    }

    @Test
    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
    void should_find_by_id() throws Exception {
        mockMvc.perform(get("/app").param("appId", "1").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.apiVersion").value(getApiVersionFromPom()))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("test"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.lang").value("nl_NL"))
                .andExpect(jsonPath("$.title").value("test title"))
                .andExpect(jsonPath("$.components").isArray())
                .andExpect(jsonPath("$.components[0].type").value("measure"));
    }

    @Test
    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
    void should_find_by_name() throws Exception {
        mockMvc.perform(get("/app").param("name", "test").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.apiVersion").value(getApiVersionFromPom()))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("test"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.lang").value("nl_NL"))
                .andExpect(jsonPath("$.title").value("test title"))
                .andExpect(jsonPath("$.components").isArray())
                .andExpect(jsonPath("$.components[0].type").value("measure"));
    }

    @Test
    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
    void should_find_by_name_and_version() throws Exception {
        mockMvc.perform(
                        get("/app")
                                .param("name", "test")
                                .param("version", "1")
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.apiVersion").value(getApiVersionFromPom()))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("test"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.lang").value("nl_NL"))
                .andExpect(jsonPath("$.title").value("test title"))
                .andExpect(jsonPath("$.components").isArray())
                .andExpect(jsonPath("$.components[0].type").value("measure"));
    }

    @Test
    /* this test changes database content */
    @Transactional
    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
    void should_return_default_lang_when_application_language_not_configured() throws Exception {
        // unset language
        applicationRepository.getReferenceById(1L).setLang(null);

        mockMvc.perform(get("/app").param("appId", "1").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.apiVersion").value(getApiVersionFromPom()))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("test"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.title").value("test title"))
                // expect default value
                .andExpect(jsonPath("$.lang").value("nl_NL"))
                .andExpect(jsonPath("$.components").isArray())
                .andExpect(jsonPath("$.components[0].type").value("measure"));
    }

    @Test
    /* this test changes database content */
    @Order(Integer.MAX_VALUE - 1)
    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
    void should_send_401_when_application_configured() throws Exception {
        applicationRepository.setAuthenticatedRequired(1L, true);

        mockMvc.perform(get("/app").param("appId", "1").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.url").value("/login"));
    }

    @Test
    /* this test changes the database content and should run as the very last */
    @Order(Integer.MAX_VALUE)
    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
    void should_error_when_no_default_application_and_using_nonexistent_id() throws Exception {
        // setup test data for this test (delete default application from metadata table)
        metadataRepository.deleteMetadataByConfigKey(Metadata.DEFAULT_APPLICATION);

        mockMvc.perform(get("/app").param("appId", "666").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is5xxServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }
}
