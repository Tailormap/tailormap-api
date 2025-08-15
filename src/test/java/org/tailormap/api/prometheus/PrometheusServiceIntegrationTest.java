/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.prometheus;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junitpioneer.jupiter.DisableIfTestFails;
import org.junitpioneer.jupiter.Stopwatch;
import org.springframework.beans.factory.annotation.Autowired;
import org.tailormap.api.annotation.PostgresIntegrationTest;

@PostgresIntegrationTest
@Stopwatch
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisableIfTestFails
@Order(Integer.MAX_VALUE)
class PrometheusServiceIntegrationTest {
  @Autowired
  private PrometheusService prometheusService;

  @Test
  @Order(1)
  void isPrometheusAvailable() {
    boolean isAvailable = prometheusService.isPrometheusAvailable();
    assertTrue(isAvailable, "Prometheus server should be available");
  }

  @Test
  @Order(2)
  void testQueryExecution() {
    final String query = "scrape_duration_seconds";
    try {
      final JsonNode root = prometheusService.executeQuery(query);
      assertEquals("success", root.path("status").asText());
      assertTrue(root.path("data").path("result").isArray(), "Results should be an array");
      assertThat(
          root.path("data").path("result").get(0).path("value").get(1).asDouble(), is(greaterThan(0.0)));
    } catch (Exception e) {
      fail("Query execution failed: " + e.getMessage());
    }
  }

  @Test
  @Order(2)
  void testQueryExecutionLargeQuery() {
    final String query =
        """
label_replace(floor(increase(tailormap_app_request_total[90d])), "type", "total", "__name__", ".*")\s
or\s
label_replace(floor(time()-max_over_time(timestamp(changes(tailormap_app_request_total[5m])>0)[90d:1m])), "type", "last_updated", "__name__", ".*")
""";
    try {
      final JsonNode root = prometheusService.executeQuery(query);
      assertEquals("success", root.path("status").asText());
      assertTrue(root.path("data").path("result").isArray(), "Results should be an array");
      assertThat(
          root.path("data").path("result").get(0).path("value").get(1).asDouble(), is(greaterThan(0.0)));
    } catch (Exception e) {
      fail("Query execution failed: " + e.getMessage());
    }
  }

  @Test
  @Order(2)
  void testQueryExecutionWithUnknownMetric() {
    final String query = "unknown_metric_name";
    try {
      JsonNode result = prometheusService.executeQuery(query);
      // Even if the metric is unknown, prometheus will still return a valid response
      assertEquals("success", result.path("status").asText());
      assertTrue(result.path("data").path("result").isArray(), "Results should be an array");
      assertEquals(0, result.path("data").path("result").size(), "Result should be empty for unknown metric");
    } catch (Exception e) {
      fail("Query execution failed: " + e.getMessage());
    }
  }

  @Test
  @Order(2)
  void testQueryExecutionWithEmptyQuery() {
    try {
      prometheusService.executeQuery("");
      fail("Expected an exception for empty query");
    } catch (IOException e) {
      assertThat(e.getMessage(), containsStringIgnoringCase("invalid parameter"));
    }
  }

  @Test
  @Order(4)
  void deleteMetric() {
    final String delete = "scrape_samples_scraped";
    try {
      prometheusService.deleteMetric(delete);

    } catch (Exception e) {
      fail("Delete failed: " + e.getMessage());
    }
  }

  @Test
  @Order(4)
  void cleanTombstones() {
    try {
      prometheusService.cleanTombstones();
    } catch (Exception e) {
      fail("Clean tombstones failed: " + e.getMessage());
    }
  }
}
