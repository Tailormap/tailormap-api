/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller;

import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.tailormap.api.TestRequestProcessor.setServletPath;
import static org.tailormap.api.prometheus.TagNames.METRICS_APP_REQUEST_COUNTER_NAME;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.tailormap.api.annotation.PostgresIntegrationTest;
import org.tailormap.api.persistence.Group;

@PostgresIntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = {
      "management.endpoints.web.exposure.include=prometheus",
      "management.endpoint.prometheus.access=read_only",
      "management.prometheus.metrics.export.enabled=true",
      "management.prometheus.metrics.export.descriptions=true",
    })
class ViewerControllerWithActuatorIntegrationTest {
  @Autowired
  private MockMvc mockMvc;

  @Value("${tailormap-api.base-path}")
  private String apiBasePath;

  @Value("${management.endpoints.web.base-path}")
  private String managementBasePath;
  /**
   * Test for the Prometheus metrics endpoint. This test checks if the endpoint is accessible and returns the expected
   * content type and pattern. The assumption is that there have been several requests made to ViewerController
   */
  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  void checkPrometheusMetricsEndpointAfterAppRequest() throws Exception {
    // generate some application-use metrics by accessing the ViewerController
    final String path = apiBasePath + "/app/default";
    mockMvc.perform(get(path).accept(MediaType.APPLICATION_JSON).with(setServletPath(path)))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON));

    // check that the metrics endpoint is accessible and returns the expected content type
    mockMvc.perform(get(managementBasePath + "/prometheus")
            .accept("text/plain")
            .param("includedNames", METRICS_APP_REQUEST_COUNTER_NAME))
        .andExpect(status().isOk())
        .andExpect(content().contentType("text/plain;version=0.0.4;charset=utf-8"))
        .andExpect(content()
            .string(containsStringIgnoringCase(
                "tailormap_app_request_total{appId=\"1\",appName=\"default\",appType=\"app\"} 1.0")));
  }
}
