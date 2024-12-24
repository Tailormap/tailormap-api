/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.tailormap.api.annotation.PostgresIntegrationTest;

@PostgresIntegrationTest
@AutoConfigureMockMvc
class ConfigurationControllerIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  ConfigurationController configurationController;

  @Value("${tailormap-api.base-path}")
  private String apiBasePath;

  @Test
  void testNotExist() throws Exception {
    mockMvc.perform(get(apiBasePath + "/config/doesNotExist"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(404))
        .andExpect(jsonPath("$.message").value("Not Found"));
  }

  @Test
  void testNotAvailableForViewer() throws Exception {
    mockMvc.perform(get(apiBasePath + "/config/default-app"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(404))
        .andExpect(jsonPath("$.message").value("Not Found"));
  }

  @Test
  void test() throws Exception {
    mockMvc.perform(get(apiBasePath + "/config/test"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.key").value("test"))
        .andExpect(jsonPath("$.value").value("test value"))
        .andExpect(jsonPath("$.object.someProperty").value(1))
        .andExpect(jsonPath("$.object.nestedObject.num").value(42));
  }
}
