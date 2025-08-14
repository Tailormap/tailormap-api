/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.prometheus;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumingThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.lang.invoke.MethodHandles;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class PrometheusResultProcessorTest {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private final PrometheusResultProcessor processor = new PrometheusResultProcessor();

  @Test
  void testCombinedQueryResults() {
    String jsonResponse =
        """
{
"status": "success",
"data": {
"resultType": "vector",
"result": [
{
"metric": {
"appId": "1",
"appName": "default",
"appType": "app",
"application": "tailormap-api",
"hostname": "localhost",
"instance": "host.docker.internal:8080",
"job": "tailormap-api-snapshot",
"type": "total"
}, "value": [1755163922.424, "35" ]},
{
"metric": {
"appId": "2",
"appName": "snapshot-geoserver",
"appType": "service",
"application": "tailormap-api",
"hostname": "localhost",
"instance": "host.docker.internal:8080",
"job": "tailormap-api-snapshot",
"type": "total"
}, "value": [1755163922.424,"5"] },
{
"metric": {
"appId": "5",
"appName": "austria",
"appType": "app",
"application": "tailormap-api",
"hostname": "localhost",
"instance": "host.docker.internal:8080",
"job": "tailormap-api-snapshot",
"type": "total"
}, "value": [1755163922.424,"17"] },
{
"metric": {
"appId": "1",
"appName": "default",
"appType": "app",
"application": "tailormap-api",
"hostname": "localhost",
"instance": "host.docker.internal:8080",
"job": "tailormap-api-snapshot",
"type": "last_updated"
}, "value": [1755163922.424,"6777" ] },
{
"metric": {
"appId": "5",
"appName": "austria",
"appType": "app",
"application": "tailormap-api",
"hostname": "localhost",
"instance": "host.docker.internal:8080",
"job": "tailormap-api-snapshot",
"type": "last_updated"
}, "value": [1755163922.424,"11577" ]},
{
"metric": {
"appId": "2",
"appName": "snapshot-geoserver",
"appType": "service",
"application": "tailormap-api",
"hostname": "localhost",
"instance": "host.docker.internal:8080",
"job": "tailormap-api-snapshot",
"type": "last_updated"
}, "value": [1755163922.424,"11577"]}
]}}
""";
    try {
      Map<String, Map<String, String>> processedResults = processor.processPrometheusResults(jsonResponse);
      assertEquals(3, processedResults.size());
      processedResults.forEach((appId, metrics) -> {
        logger.info("appId: {}\t{}", appId, metrics);

        assumingThat(
            "1".equals(appId),
            () -> assertAll(
                "Default App Metrics",
                () -> assertEquals(
                    "default", metrics.get(PrometheusResultProcessor.METRICS_APP_NAME_TAG)),
                () -> assertEquals("35", metrics.get("total")),
                () -> assertEquals("6777", metrics.get("last_updated"))));
        assumingThat(
            "2".equals(appId),
            () -> assertAll(
                "Snapshot Geoserver Metrics",
                () -> assertEquals(
                    "snapshot-geoserver",
                    metrics.get(PrometheusResultProcessor.METRICS_APP_NAME_TAG)),
                () -> assertEquals("5", metrics.get("total")),
                () -> assertEquals("11577", metrics.get("last_updated"))));
        assumingThat(
            "5".equals(appId),
            () -> assertAll(
                "Austria App Metrics",
                () -> assertEquals(
                    "austria", metrics.get(PrometheusResultProcessor.METRICS_APP_NAME_TAG)),
                () -> assertEquals("17", metrics.get("total")),
                () -> assertEquals("11577", metrics.get("last_updated"))));
      });
    } catch (JsonProcessingException e) {
      fail("Failed to process JSON response: " + e.getMessage());
    }
  }
}
