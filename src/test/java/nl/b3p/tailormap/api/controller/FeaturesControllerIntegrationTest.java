/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

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

@SpringBootTest(classes = {HSQLDBTestProfileJPAConfiguration.class, FeaturesController.class})
@AutoConfigureMockMvc
@EnableAutoConfiguration
@ActiveProfiles("test")
class FeaturesControllerIntegrationTest {
    @Autowired private MockMvc mockMvc;

    @Test
    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
    void filter_not_supported() throws Exception {
        mockMvc.perform(get("/features/1/2").param("filter", "naam=Utrecht"))
                .andExpect(status().is4xxClientError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
    void fid_not_supported() throws Exception {
        mockMvc.perform(
                        get("/features/1/2")
                                .param(
                                        "__fid",
                                        "Provinciegebied.19ce551e-bc01-46e9-b953-929318dcdf87"))
                .andExpect(status().is4xxClientError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
    void givenOnly_XorY_shouldError() throws Exception {
        mockMvc.perform(get("/features/1/2").param("x", "3"))
                .andExpect(status().is4xxClientError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(400));

        mockMvc.perform(get("/features/1/2").param("y", "3"))
                .andExpect(status().is4xxClientError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
    void given_distance_NotGreaterThanZero() throws Exception {
        mockMvc.perform(get("/features/1/2?y=3&y=3").param("distance", "0"))
                .andExpect(status().is4xxClientError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(400));

        mockMvc.perform(get("/features/1/2?y=3&y=3").param("distance", "-1"))
                .andExpect(status().is4xxClientError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
    void should_error_when_calling_with_nonexistent_appid() throws Exception {
        mockMvc.perform(get("/features/400/1"))
                .andExpect(status().is4xxClientError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
    void should_not_find_when_called_without_appid() throws Exception {
        mockMvc.perform(get("/features/")).andExpect(status().isNotFound());
    }
}
