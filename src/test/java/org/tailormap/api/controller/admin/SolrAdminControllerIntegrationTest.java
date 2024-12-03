/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.tailormap.api.annotation.PostgresIntegrationTest;
import org.tailormap.api.persistence.Group;

@AutoConfigureMockMvc
@Stopwatch
@PostgresIntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SolrAdminControllerIntegrationTest {
  @Autowired private WebApplicationContext context;
  private MockMvc mockMvc;

  private final int waitForIndexRefreshMillis = 10000;

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
  void deleteNonExistentIndex() throws Exception {
    mockMvc
        .perform(
            delete(adminBasePath + "/index/snapshot-geoserver/1000")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound());
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  @Order(1)
  void refreshIndex1() throws Exception {
    mockMvc
        .perform(
            put(adminBasePath + "/index/1")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isAccepted());
    // after submitting the request, wait for the index to be refreshed
    Thread.sleep(waitForIndexRefreshMillis);
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  @Order(2)
  void clearIndex1() throws Exception {
    mockMvc
        .perform(delete(adminBasePath + "/index/1").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNoContent());
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  @Order(3)
  void recreateIndex1() throws Exception {
    mockMvc
        .perform(put(adminBasePath + "/index/1").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isAccepted());
    // after submitting the request, wait for the index to be refreshed
    Thread.sleep(waitForIndexRefreshMillis);
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  @Order(1)
  void indexWithoutSearchIndexConfigured() throws Exception {
    mockMvc
        .perform(
            put(adminBasePath + "/index/100")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound());
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  void indexWithoutSchedule() throws Exception {
    mockMvc
        .perform(put(adminBasePath + "/index/3").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status").value(202))
        .andExpect(jsonPath("$.message").value("Indexing scheduled"))
        .andExpect(jsonPath("$.taskName").isNotEmpty());

    // after submitting the request, wait for the index to be refreshed in the scheduler
    Thread.sleep(waitForIndexRefreshMillis);
  }
}
