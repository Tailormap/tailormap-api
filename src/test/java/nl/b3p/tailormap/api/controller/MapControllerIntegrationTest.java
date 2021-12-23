/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */ package nl.b3p.tailormap.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import nl.b3p.tailormap.api.HSQLDBTestProfileJPAConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = {HSQLDBTestProfileJPAConfiguration.class, MapController.class})
@AutoConfigureMockMvc
@EnableAutoConfiguration
@ActiveProfiles("test")
class MapControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
    void should_return_data_for_configured_app() throws Exception {
        mockMvc.perform(get("/map/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.initialExtent").isMap())
                .andExpect(jsonPath("$.maxExtent").isMap())
                .andExpect(jsonPath("$.services").isArray())
                .andExpect(jsonPath("$.baseLayers").isArray())
                .andExpect(jsonPath("$.crs.code").value("EPSG:28992"));
    }

    @Test
    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
    void should_error_when_calling_with_nonexistent_id() throws Exception {
        mockMvc.perform(get("/map/400"))
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
        mockMvc.perform(get("/map/")).andExpect(status().isNotFound());
    }
}
