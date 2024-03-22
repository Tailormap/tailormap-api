/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package nl.b3p.tailormap.api.controller.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import nl.b3p.tailormap.api.annotation.PostgresIntegrationTest;
import nl.b3p.tailormap.api.persistence.Group;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.jdbc.JdbcTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@PostgresIntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApplicationAdminControllerIntegrationTest {
  @Autowired private WebApplicationContext context;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Value("${tailormap-api.admin.base-path}")
  private String adminBasePath;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private MockMvc mockMvc;
  private Long maxAppId;

  private final String appName = "test-unique";
  private final String otherAppName = "test-unique-other";
  private long createdAppId;

  @BeforeAll
  void initialize() {
    maxAppId = jdbcTemplate.queryForObject("select max(id) from application", Long.class);

    mockMvc =
        MockMvcBuilders.webAppContextSetup(context).build(); // Required for Spring Data Rest APIs
  }

  @AfterAll
  void cleanup() {
    JdbcTestUtils.deleteFromTableWhere(jdbcTemplate, "application", "id > ?", maxAppId);
  }

  private String getTestApplicationJson(String name) {
    return objectMapper
        .createObjectNode()
        .put("name", name)
        .put("crs", "EPSG:3857")
        .toPrettyString();
  }

  private String getDuplicateNameErrorJson(String name) {
    ObjectNode object = objectMapper.createObjectNode();
    ArrayNode errors = object.putArray("errors");
    ObjectNode error = errors.addObject();
    error.put("entity", "Application");
    error.put("property", "name");
    error.put("invalidValue", name);
    error.put("message", "Application with name \"" + name + "\" already exists.");
    return object.toPrettyString();
  }

  @Test
  @Order(1)
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void testCreateApplications() throws Exception {

    MvcResult result =
        mockMvc
            .perform(
                post(adminBasePath + "/applications")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(getTestApplicationJson(appName)))
            .andExpect(status().isCreated())
            .andReturn();

    createdAppId =
        objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

    mockMvc
        .perform(
            post(adminBasePath + "/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(getTestApplicationJson(otherAppName)))
        .andExpect(status().isCreated());
  }

  @Test
  @Order(2)
  @WithMockUser(
      username = "admin",
      authorities = {Group.ADMIN})
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void testCantCreateApplicationWithDuplicateName() throws Exception {
    mockMvc
        .perform(
            post(adminBasePath + "/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(getTestApplicationJson(appName)))
        .andDo(MockMvcResultHandlers.print())
        .andExpect(status().isBadRequest())
        .andExpect(content().json(getDuplicateNameErrorJson(appName)));
  }

  @Test
  @Order(3)
  @WithMockUser(
      username = "admin",
      authorities = {Group.ADMIN})
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void testCantChangeApplicationNameToDuplicate() throws Exception {
    mockMvc
        .perform(
            patch(adminBasePath + "/applications/" + createdAppId)
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
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void testChangeApplicationName() throws Exception {
    mockMvc
        .perform(
            patch(adminBasePath + "/applications/" + createdAppId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(getTestApplicationJson("some-other-name")))
        .andExpect(status().isOk());
  }
}
