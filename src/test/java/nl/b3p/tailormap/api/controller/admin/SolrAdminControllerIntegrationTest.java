/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller.admin;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import nl.b3p.tailormap.api.annotation.PostgresIntegrationTest;
import nl.b3p.tailormap.api.persistence.Group;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junitpioneer.jupiter.Stopwatch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@AutoConfigureMockMvc
@Stopwatch
@PostgresIntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SolrAdminControllerIntegrationTest {
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
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void pingTest() throws Exception {
    mockMvc
        .perform(get(adminBasePath + "/index/ping").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.status").value("OK"))
        .andExpect(jsonPath("$.timeElapsed").isNumber());
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void deleteNonExistentLayerFromIndex() throws Exception {
    mockMvc
        .perform(
            delete(adminBasePath + "/index/snapshot-geoserver/doesnotexist")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNoContent());
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  @Order(1)
  void addPostgisLayerToIndex() throws Exception {
    mockMvc
        .perform(
            put(adminBasePath + "/index/snapshot-geoserver/postgis:begroeidterreindeel")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isAccepted());
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  @Order(2)
  void deletePostgisLayerFromIndex() throws Exception {
    mockMvc
        .perform(
            delete(adminBasePath + "/index/snapshot-geoserver/postgis:begroeidterreindeel")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNoContent());
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  @Order(1)
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void indexOracleLayerWithoutSearchFields() throws Exception {
    mockMvc
        .perform(
            put(adminBasePath + "/index/snapshot-geoserver/oracle:WATERDEEL")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isInternalServerError())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(
            jsonPath("$.message").value(startsWith("No search fields configured for layer")));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  @Order(2)
  void deleteOracleLayerFromIndex() throws Exception {
    mockMvc
        .perform(
            delete(adminBasePath + "/index/snapshot-geoserver/oracle:WATERDEEL")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNoContent());
  }
}
