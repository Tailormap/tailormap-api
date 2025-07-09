/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junitpioneer.jupiter.Stopwatch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.tailormap.api.annotation.PostgresIntegrationTest;
import org.tailormap.api.persistence.Group;

@PostgresIntegrationTest
@AutoConfigureMockMvc
@Execution(ExecutionMode.CONCURRENT)
@Stopwatch
@Order(1)
public class UniqueValuesAdminControllerIntegrationTest {
  @Value("${tailormap-api.admin.base-path}/unique-values")
  private String adminUniqueValuesPath;

  @Autowired
  private MockMvc mockMvc;

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  void bronhouder_unique_values_test() throws Exception {
    String url = adminUniqueValuesPath + "/39/bronhouder";
    MvcResult result = mockMvc.perform(get(url).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.filterApplied").value(false))
        .andExpect(jsonPath("$.values").isArray())
        .andExpect(jsonPath("$.values").isNotEmpty())
        .andReturn();

    final String body = result.getResponse().getContentAsString();
    List<String> values = JsonPath.read(body, "$.values");
    final Set<String> uniqueValues = new HashSet<>(values);

    assertEquals(values.size(), uniqueValues.size(), "Unique values should be unique");
    assertTrue(uniqueValues.containsAll(Set.of("W0636", "G0344", "L0004", "W0155", "L0001", "P0026", "L0002", "G1904")), "not all values are present");
  }

}
