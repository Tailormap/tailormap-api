/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junitpioneer.jupiter.Stopwatch;
import org.junitpioneer.jupiter.displaynamegenerator.ReplaceCamelCaseAndUnderscoreAndNumber;
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
@DisplayNameGeneration(ReplaceCamelCaseAndUnderscoreAndNumber.class)
class PrometheusDataControllerIntegrationTest {
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
  void testGetApplicationGraphicData() throws Exception {
    mockMvc.perform(get(adminBasePath + "/graph/applications"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.applications").isArray())
        .andExpect(jsonPath("$.applications[0].appId").value("1"))
        .andExpect(jsonPath("$.applications[1].appId").value("5"));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  void testGetApplicationGraphicData60Days() throws Exception {
    mockMvc.perform(get(adminBasePath + "/graph/applications?numberOfDays=60"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.applications").isArray())
        .andExpect(jsonPath("$.applications[0].appId").value("1"))
        .andExpect(jsonPath("$.applications[1].appId").value("5"));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  void testGetApplicationGraphicDataInvalidNumberOfDays() throws Exception {
    mockMvc.perform(get(adminBasePath + "/graph/applications?numberOfDays=0"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.message").value("Invalid number of days provided."));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  void testGetApplicationLayersGraphicData() throws Exception {
    mockMvc.perform(get(adminBasePath + "/graph/applayers/1"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.applicationLayers").isArray())
        .andExpect(jsonPath("$.applicationLayers").isNotEmpty())
        .andExpect(jsonPath("$.applicationLayers.length()").value(4))
        .andExpect(jsonPath("$.applicationLayers[0].appId").value("1"))
        .andExpect(jsonPath("$.applicationLayers[0].appLayerId").value("lyr:openbasiskaart:osm"));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  void testGetApplicationLayersGraphicData60Days() throws Exception {
    mockMvc.perform(get(adminBasePath + "/graph/applayers/1?numberOfDays=60"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.applicationLayers").isArray())
        .andExpect(jsonPath("$.applicationLayers").isNotEmpty())
        .andExpect(jsonPath("$.applicationLayers[0].appId").value("1"))
        .andExpect(jsonPath("$.applicationLayers[0].appLayerId").exists());
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  void testGetApplicationLayersGraphicDataAppDoesNotExist() throws Exception {
    mockMvc.perform(get(adminBasePath + "/graph/applayers/1000"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.applicationLayers").isArray())
        .andExpect(jsonPath("$.applicationLayers").isEmpty());
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  void testGetApplicationLayersGraphicDataInvalidAppId() throws Exception {
    mockMvc.perform(get(adminBasePath + "/graph/applayers/0?numberOfDays=0"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.message").value("Invalid application id or number of days provided."));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  void testGetApplicationLayersGraphicDataInvalidNumberOfDays() throws Exception {
    mockMvc.perform(get(adminBasePath + "/graph/applayers/0"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.message").value("Invalid application id or number of days provided."));
  }
}
