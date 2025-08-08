/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.tailormap.api.util.Constants.METRICS_APPLAYER_SWITCHED_ON_COUNTER_NAME;
import static org.tailormap.api.util.Constants.METRICS_APP_REQUEST_COUNTER_NAME;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.tailormap.api.annotation.PostgresIntegrationTest;
import org.tailormap.api.persistence.Group;

@AutoConfigureMockMvc
@PostgresIntegrationTest
@Order(Integer.MAX_VALUE)
class PrometheusIntegrationTest {
  @Autowired
  private MockMvc mockMvc;

  @Value("${management.endpoints.web.base-path}")
  private String basePath;

  /**
   * Test for the Prometheus metrics endpoint. This test checks if the endpoint is accessible and returns the expected
   * content type and pattern. The assumption is that there have been several requests made to ViewerController
   */
  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  void prometheusMetricsEndpoint() throws Exception {
    mockMvc.perform(get(basePath + "/prometheus")
            .accept("text/plain")
            .param("includedNames", METRICS_APP_REQUEST_COUNTER_NAME)
            .param("includedNames", METRICS_APPLAYER_SWITCHED_ON_COUNTER_NAME))
        .andExpect(status().isOk())
        .andExpect(content().contentType("text/plain;version=0.0.4;charset=utf-8"))
        .andExpect(content().string(matchesPattern(".*tailormap_app_request_total.*")));
  }
}
