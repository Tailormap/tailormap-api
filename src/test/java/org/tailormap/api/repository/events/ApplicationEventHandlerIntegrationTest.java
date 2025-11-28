/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.repository.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.tailormap.api.annotation.PostgresIntegrationTest;
import org.tailormap.api.persistence.Application;
import org.tailormap.api.persistence.Group;
import org.tailormap.api.persistence.json.AppContent;
import org.tailormap.api.prometheus.PrometheusService;
import org.tailormap.api.repository.ApplicationRepository;

@Order(Integer.MAX_VALUE)
@AutoConfigureMockMvc
@PostgresIntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApplicationEventHandlerIntegrationTest {

  @Autowired
  private ApplicationEventHandler applicationEventHandler;

  @Autowired
  private ApplicationRepository applicationRepository;

  @Autowired
  private PrometheusService prometheusService;

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
  @Order(2)
  void testApplicationDeleted() throws Exception {
    Application application = applicationRepository.findByName("default");
    assertEquals(1, application.getId(), "default application should have id 1");
    assumeTrue(prometheusService.isPrometheusAvailable(), "Prometheus must be available for this test");

    // trigger the delete event handler
    applicationEventHandler.afterDeleteApplicationEventHandler(application);

    mockMvc.perform(get(adminBasePath + "/graph/applications"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.applications").isArray())
        .andExpect(jsonPath("$.applications[0].appId").value("5"));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  @Order(1)
  void testAllAppLayersRemoved() throws Exception {
    Application application = applicationRepository.findById(1L).orElseThrow();
    assumeTrue("default".equals(application.getName()), "Application with id 1 should be 'default'");
    assumeTrue(prometheusService.isPrometheusAvailable(), "Prometheus must be available for this test");
    assertTrue(
        application.getAllOldAppTreeLayerNode().findAny().isEmpty(), "No old node IDs should exist initially");

    // create a new app content (only the part we need for this test)
    final AppContent appContentCopy = new AppContent();
    // Remove all app layers and update the application
    appContentCopy.setLayerNodes(Collections.emptyList());
    appContentCopy.setBaseLayerNodes(Collections.emptyList());
    application.setContentRoot(appContentCopy);

    assertTrue(application.getAllOldAppTreeLayerNode().findAny().isPresent(), "Old node IDs should now exist");

    // trigger the delete event handler
    applicationEventHandler.beforeSaveApplicationEventHandler(application);

    mockMvc.perform(get(adminBasePath + "/graph/applayers/1"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.applicationLayers").isArray())
        .andExpect(jsonPath("$.applicationLayers").isEmpty());
  }
}
