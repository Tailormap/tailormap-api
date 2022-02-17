/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import nl.b3p.tailormap.api.HSQLDBTestProfileJPAConfiguration;
import nl.b3p.tailormap.api.TestSecurityConfig;
import nl.b3p.tailormap.api.repository.ApplicationRepository;

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

@SpringBootTest(
        classes = {
            HSQLDBTestProfileJPAConfiguration.class,
            LayersController.class,
            TestSecurityConfig.class
        })
@AutoConfigureMockMvc
@EnableAutoConfiguration
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LayersControllerIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired ApplicationRepository applicationRepository;

    @Test
    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
    void should_return_data_for_configured_app() throws Exception {
        mockMvc.perform(get("/1/layers"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0]").isMap())
                .andExpect(jsonPath("$[0].crs").isMap());
    }

    @Test
    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
    void should_error_when_calling_with_nonexistent_id() throws Exception {
        mockMvc.perform(get("/400/layers"))
                .andExpect(status().is4xxClientError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(
                        jsonPath("$.message")
                                .value("Requested an application that does not exist"));
    }

    @Test
    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
    void should_not_find_when_called_without_id() throws Exception {
        mockMvc.perform(get("/layers/")).andExpect(status().isNotFound());
    }

    @Test
    /* this test changes database content */
    @Order(Integer.MAX_VALUE)
    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
    void should_send_401_when_application_login_required() throws Exception {
        applicationRepository.setAuthenticatedRequired(1L, true);

        mockMvc.perform(get("/1/layers").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.url").value("/login"));
    }
}
