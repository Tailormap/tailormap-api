/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junitpioneer.jupiter.Stopwatch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.tailormap.api.annotation.PostgresIntegrationTest;
import org.tailormap.api.persistence.Group;

@AutoConfigureMockMvc
@Stopwatch
@PostgresIntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TaskAdminControllerIntegrationTest {
  @Autowired private WebApplicationContext context;
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
  void listAllTasks() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get(adminBasePath + "/tasks").accept(MediaType.APPLICATION_JSON))
            // .andDo(print())
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.tasks").isArray())
            .andExpect(jsonPath("$.tasks.length()").value(2))
            .andExpect(jsonPath("$.tasks[0].type").value("dummy"))
            .andExpect(jsonPath("$.tasks[1].type").value("dummy"))
            .andReturn();
    final String body = result.getResponse().getContentAsString();
    String validUUID = JsonPath.read(body, "$.tasks[0].uuid");
    assertEquals(UUID.fromString(validUUID).toString(), validUUID);

    validUUID = JsonPath.read(body, "$.tasks[1].uuid");
    assertEquals(UUID.fromString(validUUID).toString(), validUUID);
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  void listTasksForExistingDummyType() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                get(adminBasePath + "/tasks")
                    .queryParam("type", "dummy")
                    .accept(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.tasks").isArray())
            .andExpect(jsonPath("$.tasks.length()").value(2))
            .andExpect(jsonPath("$.tasks[0].type").value("dummy"))
            .andExpect(jsonPath("$.tasks[1].type").value("dummy"))
            .andReturn();

    final String body = result.getResponse().getContentAsString();
    String validUUID = JsonPath.read(body, "$.tasks[0].uuid");
    assertEquals(UUID.fromString(validUUID).toString(), validUUID);

    validUUID = JsonPath.read(body, "$.tasks[1].uuid");
    assertEquals(UUID.fromString(validUUID).toString(), validUUID);
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  void listTasksForNonExistentType() throws Exception {
    mockMvc
        .perform(
            get(adminBasePath + "/tasks")
                .queryParam("type", "does-not-exist")
                .accept(MediaType.APPLICATION_JSON))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.tasks").isArray())
        .andExpect(jsonPath("$.tasks.length()").value(0));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void detailsOfTask() throws Exception {
    mockMvc
        .perform(
            get(adminBasePath + "/tasks/{uuid}", "6308d26e-fe1e-4268-bb28-20db2cd06914")
                .accept(MediaType.APPLICATION_JSON))
        // .andDo(print())
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.uuid").value("6308d26e-fe1e-4268-bb28-20db2cd06914"))
        .andExpect(jsonPath("$.type").value("dummy"))
        .andExpect(jsonPath("$.status").value("running"))
        .andExpect(jsonPath("$.progress").value(0.5));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void startTask() throws Exception {
    mockMvc
        .perform(
            put(adminBasePath + "/tasks/{uuid}/start", "6308d26e-fe1e-4268-bb28-20db2cd06914")
                .accept(MediaType.APPLICATION_JSON))
        //                .andDo(print())
        .andExpect(status().isAccepted());
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void stopTask() throws Exception {
    mockMvc
        .perform(
            put(adminBasePath + "/tasks/{uuid}/stop", "6308d26e-fe1e-4268-bb28-20db2cd06914")
                .accept(MediaType.APPLICATION_JSON))
        //                .andDo(print())
        .andExpect(status().isAccepted());
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void deleteTask() throws Exception {
    mockMvc
        .perform(
            delete(adminBasePath + "/tasks/{uuid}", "6308d26e-fe1e-4268-bb28-20db2cd06914")
                .accept(MediaType.APPLICATION_JSON))
        //                .andDo(print())
        .andExpect(status().isNoContent());
  }
}
