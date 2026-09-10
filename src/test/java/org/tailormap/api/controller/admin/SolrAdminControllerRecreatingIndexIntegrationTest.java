/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.tailormap.api.IntegrationTestOrdering.SOLR_REINDEXING_INTEGRATION_TEST_ORDER;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junitpioneer.jupiter.RetryingTest;
import org.junitpioneer.jupiter.Stopwatch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.tailormap.api.annotation.PostgresIntegrationTest;
import org.tailormap.api.persistence.Group;

@AutoConfigureMockMvc
@Stopwatch
@PostgresIntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Execution(ExecutionMode.SAME_THREAD)
@Order(SOLR_REINDEXING_INTEGRATION_TEST_ORDER)
class SolrAdminControllerRecreatingIndexIntegrationTest {
  @Autowired
  private WebApplicationContext context;

  private MockMvc mockMvc;

  @Value("${tailormap-api.admin.base-path}")
  private String adminBasePath;

  @BeforeAll
  void initialize() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  @Order(Order.DEFAULT + 1)
  void refresh_index_4() throws Exception {
    // 4: bak
    mockMvc.perform(put(adminBasePath + "/index/4")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isAccepted());
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  @Order(Order.DEFAULT + 2)
  void clear_index_4() throws Exception {
    // 4: bak
    mockMvc.perform(delete(adminBasePath + "/index/4").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNoContent());
  }

  @RetryingTest(maxAttempts = 3, suspendForMs = 5000)
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  @Order(Order.DEFAULT + 3)
  void recreate_index_4() throws Exception {
    // 4: bak
    mockMvc.perform(put(adminBasePath + "/index/4").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isAccepted());
  }
}
