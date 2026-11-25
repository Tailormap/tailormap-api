/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller.admin;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.tailormap.api.annotation.PostgresIntegrationTest;
import org.tailormap.api.persistence.Group;
import org.tailormap.api.scheduling.TaskManagerService;
import org.tailormap.api.scheduling.TaskType;

@AutoConfigureMockMvc
@PostgresIntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.CONCURRENT)
// Creates a one-time index task that TaskAdminControllerIntegrationTest would count
@ResourceLock("scheduler-index-tasks")
class SolrAdminControllerIntegrationTest {
  @Autowired
  private WebApplicationContext context;

  @Autowired
  private TaskManagerService taskManagerService;

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
  void ping_test() throws Exception {
    mockMvc.perform(get(adminBasePath + "/index/ping").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.status").value("OK"))
        .andExpect(jsonPath("$.timeElapsed").isNumber());
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  void delete_non_existent_index() throws Exception {
    mockMvc.perform(delete(adminBasePath + "/index/snapshot-geoserver/1000").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound());
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  void index_without_search_index_configured() throws Exception {
    mockMvc.perform(put(adminBasePath + "/index/100")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound());
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  void index_without_schedule() throws Exception {
    // 3: Wegdeel
    MvcResult result = mockMvc.perform(put(adminBasePath + "/index/3").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.code").value(202))
        .andExpect(jsonPath("$.message").value("Indexing scheduled"))
        .andExpect(jsonPath("$.uuid").isNotEmpty())
        .andExpect(jsonPath("$.type").value("index"))
        .andReturn();

    // Wait for the one-time index task to finish, so it does not show up in the tasks of other tests
    final UUID taskUuid = UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.uuid"));
    Awaitility.await()
        .pollInterval(Duration.ofSeconds(1))
        .atMost(Duration.ofMinutes(2))
        .untilAsserted(() -> assertNull(taskManagerService.getJobKey(TaskType.INDEX, taskUuid)));
  }
}
