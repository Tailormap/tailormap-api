/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller.admin;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junitpioneer.jupiter.Stopwatch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.tailormap.api.annotation.PostgresIntegrationTest;
import org.tailormap.api.persistence.Group;
import org.tailormap.api.repository.FeatureSourceRepository;
import org.tailormap.api.repository.FeatureTypeRepository;

@PostgresIntegrationTest
@AutoConfigureMockMvc
@Execution(ExecutionMode.CONCURRENT)
@Stopwatch
class UniqueValuesAdminControllerIntegrationTest {
  @Value("${tailormap-api.admin.base-path}/unique-values/")
  private String adminUniqueValuesPath;

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private FeatureTypeRepository featureTypeRepository;

  @Autowired
  private FeatureSourceRepository featureSourceRepository;

  private long featureTypeId;

  @BeforeEach
  void setUp() {
    featureTypeId = featureTypeRepository
        .getTMFeatureTypeByNameAndFeatureSource(
            "bak",
            featureSourceRepository
                .getByTitle("PostGIS")
                .orElseThrow(() -> new NoSuchElementException("PostGIS feature source not found")))
        .orElseThrow(() -> new NoSuchElementException("Feature type bak not found"))
        .getId();
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  void postgis_bak_bronhouder_unique_values_test() throws Exception {
    String url = adminUniqueValuesPath + featureTypeId + "/bronhouder";
    MvcResult result = mockMvc.perform(get(url).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.filterApplied").value(false))
        .andExpect(jsonPath("$.values").isArray())
        .andExpect(jsonPath("$.values").isNotEmpty())
        .andReturn();

    final List<String> values = JsonPath.read(result.getResponse().getContentAsString(), "$.values");

    assertThat("There should be 3 unique values", values, hasSize(3));
    assertThat("Not all values are present", values, containsInAnyOrder("G0344", "P0026", "G1904"));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  void postgis_bak_bronhouder_unique_values_filtered_test() throws Exception {
    String url = adminUniqueValuesPath + featureTypeId + "/bronhouder";
    MvcResult result = mockMvc.perform(
            get(url).param("filter", "bronhouder = 'G0344'").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.filterApplied").value(true))
        .andExpect(jsonPath("$.values").isArray())
        .andExpect(jsonPath("$.values").isNotEmpty())
        .andReturn();

    final List<String> values = JsonPath.read(result.getResponse().getContentAsString(), "$.values");

    assertEquals(1, values.size(), "There should only be 1 unique value");
    assertTrue(values.contains("G0344"), "Value not present");
  }
}
