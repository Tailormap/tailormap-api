/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.security;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.tailormap.api.annotation.PostgresIntegrationTest;
import org.tailormap.api.persistence.Group;

/**
 * Integration test for the actuator security configuration. The test verifies that the actuator endpoints are secured
 * as expected; {@code health} is publicly accessible, {@code prometheus} requires authentication.
 */
@PostgresIntegrationTest
@AutoConfigureMockMvc
@AutoConfigureObservability
@TestPropertySource(
    properties = {
      "management.endpoints.web.exposure.include=health,prometheus",
      "management.endpoint.health.access=read_only",
      "management.endpoint.prometheus.access=read_only",
      "management.prometheus.metrics.export.enabled=true",
      "management.prometheus.metrics.export.descriptions=true",
    })
class ActuatorSecurityConfigurationIntegrationTest {
  @Autowired
  private MockMvc mockMvc;

  @Value("${management.endpoints.web.base-path}")
  private String basePath;

  @Test
  void testUnAuthenticatedHealth() throws Exception {
    mockMvc.perform(get(basePath + "/health").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(content().json("{\"status\":\"UP\"}"));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  void testAuthenticatedHealth() throws Exception {
    mockMvc.perform(get(basePath + "/health").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(content().json("{\"status\":\"UP\"}"));
  }

  @Test
  void testUnAuthenticatedPrometheus() throws Exception {
    mockMvc.perform(get(basePath + "/prometheus").accept(MediaType.TEXT_PLAIN))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  void testAuthenticatedPrometheus() throws Exception {
    mockMvc.perform(get(basePath + "/prometheus").accept(MediaType.TEXT_PLAIN))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
        .andExpect(content().string(startsWith("# HELP")))
        .andExpect(content().string(containsString("# HELP jvm_memory_used_bytes The amount of used memory")));
  }
}
