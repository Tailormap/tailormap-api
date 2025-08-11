/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.tailormap.api.TestRequestProcessor.setServletPath;
import static org.tailormap.api.util.Constants.METRICS_APPLAYER_SWITCHED_ON_COUNTER_NAME;
import static org.tailormap.api.util.Constants.METRICS_APP_REQUEST_COUNTER_NAME;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junitpioneer.jupiter.RetryingTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;
import org.tailormap.api.annotation.PostgresIntegrationTest;
import org.tailormap.api.persistence.Group;

@AutoConfigureMockMvc
@PostgresIntegrationTest
@Order(Integer.MAX_VALUE)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestPropertySource(
    properties = {
      "management.endpoints.web.exposure.include=prometheus",
      "management.endpoint.prometheus.access=read_only",
      "management.prometheus.metrics.export.enabled=true",
      "management.prometheus.metrics.export.descriptions=true",
    })
class PrometheusIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Value("${tailormap-api.base-path}")
  private String basePath;

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
  @Order(1)
  void prometheusMetricsEndpoint() throws Exception {
    // generate some application-use metrics by accessing the ViewerController
    for (String path : new String[] {"/app/default", "/app/austria", "/service/snapshot-geoserver"}) {
      mockMvc.perform(get(basePath + path).with(setServletPath(basePath + path)))
          .andExpect(status().isOk())
          .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    // wait for the metrics to be collected by the Prometheus container
    Thread.sleep(7000);

    mockMvc.perform(get(managementBasePath + "/prometheus")
            .accept("text/plain")
            .param("includedNames", METRICS_APP_REQUEST_COUNTER_NAME)
            .param("includedNames", METRICS_APPLAYER_SWITCHED_ON_COUNTER_NAME))
        .andExpect(status().isOk())
        .andExpect(content().contentType("text/plain;version=0.0.4;charset=utf-8"))
        .andExpect(content().string(containsStringIgnoringCase("# HELP tailormap_app_request_total")))
        .andExpect(content().string(containsStringIgnoringCase("# TYPE tailormap_app_request_total counter")))
        .andExpect(content()
            .string(containsStringIgnoringCase(
                "tailormap_app_request_total{appId=\"1\",appName=\"default\",appType=\"app\"} 1.0")))
        .andExpect(
            content()
                .string(
                    containsStringIgnoringCase(
                        "tailormap_app_request_total{appId=\"2\",appName=\"snapshot-geoserver\",appType=\"service\"} 1.0")))
        .andExpect(content()
            .string(containsStringIgnoringCase(
                "tailormap_app_request_total{appId=\"5\",appName=\"austria\",appType=\"app\"} 1.0")));
  }

  @RetryingTest(maxAttempts = 3, suspendForMs = 5000)
  @Order(Integer.MAX_VALUE)
  void prometheusAppCounters() throws Exception {
    // Example using RestTemplate to call the Prometheus API directly
    // This assumes that the Prometheus server is running on localhost:9090
    // and that the tailormap_app_request_total metric is available.

    // get the total count over the last 90 days
    final String promUrl =
        "http://localhost:9090/api/v1/query?query=floor(increase(tailormap_app_request_total[90d]))";
    ResponseEntity<String> response = new RestTemplate().getForEntity(promUrl, String.class);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(
        "application/json",
        Objects.requireNonNull(response.getHeaders().getContentType()).toString());

    JsonNode root = new ObjectMapper().readTree(response.getBody());
    assertEquals("success", root.path("status").asText());
    assertTrue(root.path("data").path("result").isArray());
    assertEquals(3, root.path("data").path("result").size());
    assertEquals(
        "tailormap-api",
        root.path("data")
            .path("result")
            .get(0)
            .path("metric")
            .path("application")
            .asText());
    // Check that total count is greater than 0
    assertThat(root.path("data").path("result").get(0).path("value").get(1).asInt(), is(greaterThan(0)));
  }
}
