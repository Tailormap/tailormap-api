/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller.admin;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.tailormap.api.scheduling.Task.TYPE_KEY;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
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
import org.tailormap.api.repository.SearchIndexRepository;
import org.tailormap.api.scheduling.TaskType;

@AutoConfigureMockMvc
@Stopwatch
@PostgresIntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TaskAdminControllerIntegrationTest {
  @Autowired
  private WebApplicationContext context;

  @Autowired
  private SearchIndexRepository searchIndexRepository;

  private final String TEST_TASK_TYPE = TaskType.INDEX.getValue();

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
    MvcResult result = mockMvc.perform(get(adminBasePath + "/tasks").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.tasks").isArray())
        .andExpect(jsonPath("$.tasks.length()").value(3))
        // value is any of the available task types
        .andExpect(jsonPath("$.tasks[0].type")
            .value(anyOf(is(TaskType.INDEX.getValue()), is(TaskType.PROMETHEUS_PING.getValue()))))
        .andExpect(jsonPath("$.tasks[1].type")
            .value(anyOf(is(TaskType.INDEX.getValue()), is(TaskType.PROMETHEUS_PING.getValue()))))
        .andExpect(jsonPath("$.tasks[2].type")
            .value(anyOf(is(TaskType.INDEX.getValue()), is(TaskType.PROMETHEUS_PING.getValue()))))
        .andReturn();
    final String body = result.getResponse().getContentAsString();
    String validUUID = JsonPath.read(body, "$.tasks[0].uuid");
    assertEquals(UUID.fromString(validUUID).toString(), validUUID);

    validUUID = JsonPath.read(body, "$.tasks[1].uuid");
    assertEquals(UUID.fromString(validUUID).toString(), validUUID);

    validUUID = JsonPath.read(body, "$.tasks[2].uuid");
    assertEquals(UUID.fromString(validUUID).toString(), validUUID);
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  void listTasksForExistingType() throws Exception {
    MvcResult result = mockMvc.perform(get(adminBasePath + "/tasks")
            .queryParam(TYPE_KEY, TEST_TASK_TYPE)
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.tasks").isArray())
        .andExpect(jsonPath("$.tasks.length()").value(2))
        .andExpect(jsonPath("$.tasks[0].type").value(TEST_TASK_TYPE))
        .andExpect(jsonPath("$.tasks[1].type").value(TEST_TASK_TYPE))
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
    mockMvc.perform(get(adminBasePath + "/tasks")
            .queryParam(TYPE_KEY, "does-not-exist")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.tasks").isArray())
        .andExpect(jsonPath("$.tasks.length()").value(0));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  void detailsOfTask() throws Exception {
    MvcResult result = mockMvc.perform(get(adminBasePath + "/tasks")
            .queryParam(TYPE_KEY, TEST_TASK_TYPE)
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.tasks").isArray())
        .andExpect(jsonPath("$.tasks.length()").value(2))
        .andReturn();

    final String detailsUUID = JsonPath.read(result.getResponse().getContentAsString(), "$.tasks[0].uuid");
    final String detailsType = JsonPath.read(result.getResponse().getContentAsString(), "$.tasks[0].type");

    mockMvc.perform(get(adminBasePath + "/tasks/{type}/{uuid}", detailsType, detailsUUID)
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.uuid").value(detailsUUID))
        .andExpect(jsonPath("$.type").value(detailsType));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  void startNonExistentTask() throws Exception {
    mockMvc.perform(put(
                adminBasePath + "/tasks/{type}/{uuid}/start",
                TEST_TASK_TYPE,
                "6308d26e-fe1e-4268-bb28-20db2cd06914")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.message").value("Task not found"));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  void stopUnstoppableTask() throws Exception {
    MvcResult result = mockMvc.perform(get(adminBasePath + "/tasks")
            .queryParam(TYPE_KEY, TEST_TASK_TYPE)
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.tasks").isArray())
        .andExpect(jsonPath("$.tasks.length()").value(2))
        .andReturn();

    final String unstoppableUUID = JsonPath.read(result.getResponse().getContentAsString(), "$.tasks[0].uuid");
    final String unstoppableType = JsonPath.read(result.getResponse().getContentAsString(), "$.tasks[0].type");

    mockMvc.perform(put(adminBasePath + "/tasks/{type}/{uuid}/stop", unstoppableType, unstoppableUUID)
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.message").value("Task cannot be stopped"));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  void deleteNonExistentTask() throws Exception {
    mockMvc.perform(delete(
                adminBasePath + "/tasks/{type}/{uuid}",
                TEST_TASK_TYPE,
                /* this uuid does not exist */ "6308d26e-fe1e-4268-bb28-20db2cd06914")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.message").value("Task not found"));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  @Order(Integer.MAX_VALUE)
  void deleteTask() throws Exception {
    MvcResult result = mockMvc.perform(get(adminBasePath + "/tasks")
            .queryParam(TYPE_KEY, TEST_TASK_TYPE)
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.tasks").isArray())
        .andExpect(jsonPath("$.tasks.length()").value(1))
        .andReturn();

    final String deleteUUID = JsonPath.read(result.getResponse().getContentAsString(), "$.tasks[0].uuid");

    mockMvc.perform(delete(adminBasePath + "/tasks/{type}/{uuid}", TEST_TASK_TYPE, deleteUUID)
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNoContent());
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  @Order(Integer.MAX_VALUE)
  void deleteSearchIndexTask() throws Exception {
    MvcResult result = mockMvc.perform(get(adminBasePath + "/tasks")
            .queryParam(TYPE_KEY, TaskType.INDEX.getValue())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.tasks").isArray())
        .andExpect(jsonPath("$.tasks.length()").value(2))
        .andReturn();

    final String deleteUUID = JsonPath.read(result.getResponse().getContentAsString(), "$.tasks[0].uuid");

    mockMvc.perform(delete(adminBasePath + "/tasks/{type}/{uuid}", TaskType.INDEX, deleteUUID)
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNoContent());

    assertTrue(searchIndexRepository
        .findByTaskScheduleUuid(UUID.fromString(deleteUUID))
        .isEmpty());
  }
}
