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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
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
      // disable, because we don't have an SMTP service in this test
      "management.health.mail.enabled=false"
    })
class ActuatorSecurityConfigurationIntegrationTest {
  @Autowired
  private MockMvc mockMvc;

  @Value("${management.endpoints.web.base-path}")
  private String basePath;

  @Test
  void unauthenticated_health() throws Exception {
    mockMvc.perform(get(basePath + "/health").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(content().json("{\"status\":\"UP\"}"));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  void authenticated_health() throws Exception {
    mockMvc.perform(get(basePath + "/health").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(content().json("{\"status\":\"UP\"}"));
  }

  @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
  private static final RequestPostProcessor internetRequest = (MockHttpServletRequest request) -> {
    request.setRemoteAddr("8.8.8.8");
    return request;
  };

  @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
  private static final RequestPostProcessor localNetworkRequest = (MockHttpServletRequest request) -> {
    request.setRemoteAddr("172.1.1.1");
    return request;
  };

  @Test
  void unauthenticated_prometheus_from_internet() throws Exception {
    mockMvc.perform(get(basePath + "/prometheus")
            .accept(MediaType.TEXT_PLAIN)
            .with(internetRequest))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void unauthenticated_prometheus_from_local_network() throws Exception {
    mockMvc.perform(get(basePath + "/prometheus")
            .accept(MediaType.TEXT_PLAIN)
            .with(localNetworkRequest))
        .andExpect(status().isOk())
        .andExpect(content().string(startsWith("# HELP")));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  void authenticated_prometheus_from_internet() throws Exception {
    mockMvc.perform(get(basePath + "/prometheus")
            .accept(MediaType.TEXT_PLAIN)
            .with(internetRequest))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
        .andExpect(content().string(startsWith("# HELP")))
        .andExpect(content().string(containsString("# HELP jvm_memory_used_bytes The amount of used memory")));
  }
}
