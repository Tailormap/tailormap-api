/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.Assumptions.assumingThat;
import static org.tailormap.api.prometheus.TagNames.METRICS_APP_ID_TAG;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Objects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junitpioneer.jupiter.RetryingTest;
import org.junitpioneer.jupiter.displaynamegenerator.ReplaceCamelCaseAndUnderscoreAndNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.tailormap.api.annotation.PostgresIntegrationTest;
import org.tailormap.api.prometheus.PrometheusResultProcessor;

/**
 * Integration tests for the Prometheus service. These tests assume that the Prometheus server is running on
 * localhost:9090 and that the tailormap_app_request_total metric is available.
 */
@PostgresIntegrationTest
@Order(Integer.MAX_VALUE)
@DisplayNameGeneration(ReplaceCamelCaseAndUnderscoreAndNumber.class)
public class PrometheusIntegrationTest {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Value("${tailormap-api.prometheus-api-url:http://localhost:9090/api/v1}")
  private String prometheusUrl;

  // The number of applications we expect to have metrics for, CI setup has 1 app
  private final int countedApps = 1;
  // get the total count over the last 90 days
  final String totalsQuery = "floor(increase(tailormap_app_request_total[90d]))";

  // get the last update within the last 90 days
  final String counterLastUpdatedQuery =
      "floor(time()-max_over_time(timestamp(changes(tailormap_app_request_total[5m])>0)[90d:1m]))";

  @BeforeAll
  static void checkIfPrometheusIsUpOnLocalhost() {
    ResponseEntity<String> response =
        new RestTemplate().getForEntity("http://localhost:9090/query?query=up", String.class);
    assumeTrue(
        HttpStatus.OK.isSameCodeAs(response.getStatusCode()),
        "Prometheus is not running on localhost:9090, skipping Prometheus integration tests.");
  }

  @Tag("prometheus-service-testcase")
  @RetryingTest(maxAttempts = 3, suspendForMs = 5000)
  void prometheusAppCountersOver90days() throws Exception {
    ResponseEntity<String> response =
        new RestTemplate().getForEntity(prometheusUrl + "/query?query=" + totalsQuery, String.class);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(
        "application/json",
        Objects.requireNonNull(response.getHeaders().getContentType()).toString());

    JsonNode root = new ObjectMapper().readTree(response.getBody());
    logger.debug("App usage response: {}", root.toPrettyString());
    assertEquals("success", root.path("status").asText());
    assertTrue(root.path("data").path("result").isArray());
    assertEquals(countedApps, root.path("data").path("result").size());
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

  @Tag("prometheus-service-testcase")
  @RetryingTest(maxAttempts = 3, suspendForMs = 5000)
  void prometheusAppCountersLastUpdated() throws Exception {
    ResponseEntity<String> response = new RestTemplate()
        .getForEntity(prometheusUrl + "/query?query=" + counterLastUpdatedQuery, String.class);
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(
        "application/json",
        Objects.requireNonNull(response.getHeaders().getContentType()).toString());

    JsonNode root = new ObjectMapper().readTree(response.getBody());
    logger.debug("App usage last updated response: {}", root.toPrettyString());
    assertEquals("success", root.path("status").asText());
    assertTrue(root.path("data").path("result").isArray());
    assertEquals(
        countedApps,
        root.path("data").path("result").size(),
        () -> "Expected " + countedApps + " (countedApps) apps, but got "
            + root.path("data").path("result").size());
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

  @Tag("prometheus-service-testcase")
  @RetryingTest(maxAttempts = 3, suspendForMs = 5000)
  void prometheusAppCountersCombinedWithOr() throws Exception {
    final String completeQuery =
        "label_replace(" + totalsQuery + ", \"type\", \"totalCount\", \"__name__\", \".*\")"
            + " or label_replace(" + counterLastUpdatedQuery
            + ", \"type\", \"lastUpdateSecondsAgo\", \"__name__\", \".*\")";
    logger.debug("Complete query: {}", completeQuery);

    ResponseEntity<String> response =
        new RestTemplate().getForEntity(prometheusUrl + "/query?query=" + completeQuery, String.class);
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(
        "application/json",
        Objects.requireNonNull(response.getHeaders().getContentType()).toString());

    JsonNode root = new ObjectMapper().readTree(response.getBody());
    logger.debug("App usage last updated response: {}", root.toPrettyString());
    assertEquals("success", root.path("status").asText());
    assertTrue(root.path("data").path("result").isArray());
    assertEquals(
        2 * countedApps,
        root.path("data").path("result").size(),
        () -> "Expected " + (2 * countedApps) + " results, but got "
            + root.path("data").path("result").size());
  }

  @Tag("prometheus-service-testcase")
  @RetryingTest(maxAttempts = 3, suspendForMs = 5000)
  void prometheusAppCountersSeparateQueriedCombinedResults() {
    ResponseEntity<String> response = new RestTemplate()
        .getForEntity(
            prometheusUrl + "/query?query=" +
                // we need to relabel so we can merge the results later
                "label_replace("
                + totalsQuery + ", \"type\", \"totalCount\", \"__name__\", \".*\")",
            String.class);
    assertEquals(HttpStatus.OK, response.getStatusCode());

    ResponseEntity<String> response2 = new RestTemplate()
        .getForEntity(
            prometheusUrl + "/query?query=" +
                // we need to relabel so we can merge the results later
                "label_replace("
                + counterLastUpdatedQuery
                + ", \"type\", \"lastUpdateSecondsAgo\", \"__name__\", \".*\")",
            String.class);
    assertEquals(HttpStatus.OK, response2.getStatusCode());

    try {
      JsonNode root = new ObjectMapper().readTree(response.getBody());
      JsonNode root2 = new ObjectMapper().readTree(response2.getBody());
      PrometheusResultProcessor processor = new PrometheusResultProcessor();
      ArrayNode combined = processor.processPrometheusResultsToJsonArray(root, root2);
      assertEquals(
          countedApps,
          combined.size(),
          () -> "Expected " + countedApps + " (countedApps) apps, but got " + combined.size());
      combined.forEach(metricsNode -> {
        String appId = metricsNode.get(METRICS_APP_ID_TAG).asText();
        logger.debug("Metrics for appId {}: {}", appId, metricsNode);
        assumingThat(
            "1".equals(appId),
            () -> assertAll(
                "Default App Metrics",
                () -> assertEquals(
                    "default",
                    metricsNode
                        .get(PrometheusResultProcessor.METRICS_APP_NAME_TAG)
                        .asText())));
        //        assumingThat(
        //            "2".equals(appId),
        //            () -> assertAll(
        //                "Snapshot Geoserver Metrics",
        //                () -> assertEquals(
        //                    "snapshot-geoserver",
        //                    metricsNode
        //                        .get(PrometheusResultProcessor.METRICS_APP_NAME_TAG)
        //                        .asText())));
        //        assumingThat(
        //            "5".equals(appId),
        //            () -> assertAll(
        //                "Austria App Metrics",
        //                () -> assertEquals(
        //                    "austria",
        //                    metricsNode
        //                        .get(PrometheusResultProcessor.METRICS_APP_NAME_TAG)
        //                        .asText())));

        assertAll(
            () -> assertThat(metricsNode.get("totalCount").asInt(), is(greaterThan(0))),
            () -> assertThat(metricsNode.get("lastUpdateSecondsAgo").asInt(), is(greaterThan(0))));
      });
    } catch (IOException e) {
      fail("Error processing Prometheus results", e);
    }
  }
}
