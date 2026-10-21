/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.controller.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.tailormap.api.TestJson.json;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.jdbc.JdbcTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.tailormap.api.annotation.PostgresIntegrationTest;
import org.tailormap.api.persistence.Group;
import tools.jackson.databind.json.JsonMapper;

@PostgresIntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Execution(value = ExecutionMode.SAME_THREAD, reason = "we need specific ordering of tests")
class ApplicationAdminControllerIntegrationTest {
  @Autowired
  private WebApplicationContext context;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Value("${tailormap-api.admin.base-path}")
  private String adminBasePath;

  private MockMvc mockMvc;
  private Long maxAppId;

  private final String appName = "test-unique";
  private final String otherAppName = "test-unique-other";
  private long createdAppId;

  @BeforeAll
  void initialize() {
    maxAppId = jdbcTemplate.queryForObject("select max(id) from application", Long.class);

    mockMvc = MockMvcBuilders.webAppContextSetup(context).build(); // Required for Spring Data Rest APIs
  }

  @AfterAll
  void cleanup() {
    JdbcTestUtils.deleteFromTableWhere(jdbcTemplate, "application", "id > ?", maxAppId);
  }

  private static String getTestApplicationJson(String name) {
    return json(Map.of("name", name, "crs", "EPSG:3857"));
  }

  private static String getDuplicateNameErrorJson(String name) {
    return json(Map.of(
        "errors",
        List.of(Map.of(
            "entity",
            "Application",
            "property",
            "name",
            "invalidValue",
            name,
            "message",
            "Application with name \"" + name + "\" already exists."))));
  }

  @Test
  @Order(1)
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  void create_applications() throws Exception {
    MvcResult result = mockMvc.perform(post(adminBasePath + "/applications")
            .contentType(MediaType.APPLICATION_JSON)
            .content(getTestApplicationJson(appName)))
        .andExpect(status().isCreated())
        .andReturn();

    createdAppId = JsonMapper.shared()
        .readTree(result.getResponse().getContentAsString())
        .get("id")
        .asLong();

    mockMvc.perform(post(adminBasePath + "/applications")
            .contentType(MediaType.APPLICATION_JSON)
            .content(getTestApplicationJson(otherAppName)))
        .andExpect(status().isCreated());
  }

  @Test
  @Order(2)
  @WithMockUser(
      username = "admin",
      authorities = {Group.ADMIN})
  void cannot_create_application_with_duplicate_name() throws Exception {
    mockMvc.perform(post(adminBasePath + "/applications")
            .contentType(MediaType.APPLICATION_JSON)
            .content(getTestApplicationJson(appName)))
        .andExpect(status().isBadRequest())
        .andExpect(content().json(getDuplicateNameErrorJson(appName)));
  }

  @Test
  @Order(3)
  @WithMockUser(
      username = "admin",
      authorities = {Group.ADMIN})
  void cannot_change_application_name_to_duplicate() throws Exception {
    mockMvc.perform(patch(adminBasePath + "/applications/" + createdAppId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(getTestApplicationJson(otherAppName)))
        .andExpect(status().isBadRequest())
        .andExpect(content().json(getDuplicateNameErrorJson(otherAppName)));
  }

  @Test
  @Order(4)
  @WithMockUser(
      username = "admin",
      authorities = {Group.ADMIN})
  void change_application_name() throws Exception {
    mockMvc.perform(patch(adminBasePath + "/applications/" + createdAppId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(getTestApplicationJson("some-other-name")))
        .andExpect(status().isOk());
  }
}
