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
import static org.tailormap.api.prometheus.TagNames.METRICS_APP_ID_TAG;
import static org.tailormap.api.prometheus.TagNames.METRICS_APP_LAYER_ID_TAG;
import static org.tailormap.api.prometheus.TagNames.METRICS_APP_NAME_TAG;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class PrometheusResultProcessorTest {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private final PrometheusResultProcessor processor = new PrometheusResultProcessor();

  @Test
  void testCombinedQueryResultsApplication() {
    final String jsonResponse =
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
      ObjectMapper objectMapper = new ObjectMapper();
      JsonNode root = objectMapper.readTree(jsonResponse);
      Collection<Map<String, String>> processedResults = processor.processPrometheusResultsForApplications(root);
      assertEquals(3, processedResults.size());
      processedResults.forEach((metric) -> {
        String appId = metric.get(METRICS_APP_ID_TAG);
        logger.debug("appId: {}\t{}", appId, metric);

        assumingThat(
            "1".equals(appId),
            () -> assertAll(
                "Default App Metrics",
                () -> assertEquals("default", metric.get(METRICS_APP_NAME_TAG)),
                () -> assertEquals("35", metric.get("total")),
                () -> assertEquals("6777", metric.get("last_updated"))));
        assumingThat(
            "2".equals(appId),
            () -> assertAll(
                "Snapshot Geoserver Metrics",
                () -> assertEquals("snapshot-geoserver", metric.get(METRICS_APP_NAME_TAG)),
                () -> assertEquals("5", metric.get("total")),
                () -> assertEquals("11577", metric.get("last_updated"))));
        assumingThat(
            "5".equals(appId),
            () -> assertAll(
                "Austria App Metrics",
                () -> assertEquals("austria", metric.get(METRICS_APP_NAME_TAG)),
                () -> assertEquals("17", metric.get("total")),
                () -> assertEquals("11577", metric.get("last_updated"))));
      });
    } catch (JsonProcessingException e) {
      fail("Failed to process JSON response: " + e.getMessage());
    }
  }

  @Test
  void testCombinedQueryResultsApplicationLayers() {
    final String jsonResponse =
        """
{
"status" : "success",
"data" : {
"resultType" : "vector",
"result" : [ {
"metric" : {
"appId" : "1",
"appLayerId" : "lyr:openbasiskaart:osm",
"appName" : "default",
"appType" : "app",
"application" : "tailormap-api",
"hostname" : "localhost",
"type" : "totalCount"
},
"value" : [ 1.755610394271E9, "1973" ]
}, {
"metric" : {
"appId" : "1",
"appLayerId" : "lyr:snapshot-geoserver:postgis:bak",
"appName" : "default",
"appType" : "app",
"application" : "tailormap-api",
"hostname" : "localhost",
"type" : "totalCount"
},
"value" : [ 1.755610394271E9, "2361" ]
}, {
"metric" : {
"appId" : "1",
"appLayerId" : "lyr:snapshot-geoserver:postgis:begroeidterreindeel",
"appName" : "default",
"appType" : "app",
"application" : "tailormap-api",
"hostname" : "localhost",
"type" : "totalCount"
},
"value" : [ 1.755610394271E9, "2323" ]
}, {
"metric" : {
"appId" : "1",
"appLayerId" : "lyr:snapshot-geoserver:postgis:kadastraal_perceel",
"appName" : "default",
"appType" : "app",
"application" : "tailormap-api",
"hostname" : "localhost",
"type" : "totalCount"
},
"value" : [ 1.755610394271E9, "1824" ]
}, {
"metric" : {
"appId" : "1",
"appLayerId" : "lyr:openbasiskaart:osm",
"appName" : "default",
"appType" : "app",
"application" : "tailormap-api",
"hostname" : "localhost",
"type" : "lastUpdateSecondsAgo"
},
"value" : [ 1.755610394271E9, "5474" ]
}, {
"metric" : {
"appId" : "1",
"appLayerId" : "lyr:snapshot-geoserver:postgis:bak",
"appName" : "default",
"appType" : "app",
"application" : "tailormap-api",
"hostname" : "localhost",
"type" : "lastUpdateSecondsAgo"
},
"value" : [ 1.755610394271E9, "5474" ]
}, {
"metric" : {
"appId" : "1",
"appLayerId" : "lyr:snapshot-geoserver:postgis:begroeidterreindeel",
"appName" : "default",
"appType" : "app",
"application" : "tailormap-api",
"hostname" : "localhost",
"type" : "lastUpdateSecondsAgo"
},
"value" : [ 1.755610394271E9, "5474" ]
}, {
"metric" : {
"appId" : "1",
"appLayerId" : "lyr:snapshot-geoserver:postgis:kadastraal_perceel",
"appName" : "default",
"appType" : "app",
"application" : "tailormap-api",
"hostname" : "localhost",
"type" : "lastUpdateSecondsAgo"
},
"value" : [ 1.755610394271E9, "5474" ]
} ]
}
}
""";
    try {
      ObjectMapper objectMapper = new ObjectMapper();
      JsonNode root = objectMapper.readTree(jsonResponse);
      Collection<Map<String, String>> processedResults =
          processor.processPrometheusResultsForApplicationLayers(root);
      assertEquals(4, processedResults.size());

      processedResults.forEach((metric) -> {
        String appLayerId = metric.get(METRICS_APP_LAYER_ID_TAG);
        logger.debug("appLayerId: {}\t{}", appLayerId, metric);
        assumingThat(
            "lyr:openbasiskaart:osm".equals(appLayerId),
            () -> assertAll(
                "Default App Layer Metrics",
                () -> assertEquals("default", metric.get(METRICS_APP_NAME_TAG)),
                () -> assertEquals("1", metric.get("appId")),
                () -> assertEquals("1973", metric.get("totalCount")),
                () -> assertEquals("5474", metric.get("lastUpdateSecondsAgo"))));
        assumingThat(
            "lyr:snapshot-geoserver:postgis:bak".equals(appLayerId),
            () -> assertAll(
                "Snapshot Geoserver App Layer Metrics",
                () -> assertEquals("default", metric.get(METRICS_APP_NAME_TAG)),
                () -> assertEquals("1", metric.get("appId")),
                () -> assertEquals("2361", metric.get("totalCount")),
                () -> assertEquals("5474", metric.get("lastUpdateSecondsAgo"))));
        assumingThat(
            "lyr:snapshot-geoserver:postgis:begroeidterreindeel".equals(appLayerId),
            () -> assertAll(
                "Snapshot Geoserver App Layer Begroeid Terreindeel Metrics",
                () -> assertEquals("default", metric.get(METRICS_APP_NAME_TAG)),
                () -> assertEquals("1", metric.get("appId")),
                () -> assertEquals("2323", metric.get("totalCount")),
                () -> assertEquals("5474", metric.get("lastUpdateSecondsAgo"))));
        assumingThat(
            "lyr:snapshot-geoserver:postgis:kadastraal_perceel".equals(appLayerId),
            () -> assertAll(
                "Snapshot Geoserver App Layer Kadastraal Perceel Metrics",
                () -> assertEquals("default", metric.get(METRICS_APP_NAME_TAG)),
                () -> assertEquals("1", metric.get("appId")),
                () -> assertEquals("1824", metric.get("totalCount")),
                () -> assertEquals("5474", metric.get("lastUpdateSecondsAgo"))));
      });

    } catch (JsonProcessingException e) {
      fail("Failed to process JSON response: " + e.getMessage());
    }
  }
}
