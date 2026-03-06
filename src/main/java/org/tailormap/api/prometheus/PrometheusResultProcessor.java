/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.prometheus;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;

@Component
public class PrometheusResultProcessor implements TagNames {

  /**
   * Processes the JSON response from a Prometheus query and groups the results by
   * {@link TagNames#METRICS_APP_ID_TAG}. Each appId will have a map of metrics, where the key is the metric type
   * (e.g., "total", "lastUpdated") and the value is the metric value. Additionally, the appName is included in the
   * map for each appId.
   *
   * @param root the root JSON node containing the Prometheus query results (expected structure: root.data.result)
   *     where each result has a "metric" object with "appId", "type", and "value" fields, and a "value" array where
   *     the second element is the metric value. The "metric" object may also contain "appName".
   * @return a map where the key is the appId and the value is another map containing metric types as keys and their
   *     corresponding values. The inner map also includes the appName under the key "appName". If an appId has no
   *     metrics, it will not be included in the results.
   * @see TagNames#METRICS_APP_ID_TAG
   */
  public Collection<Map<String, String>> processPrometheusResultsForApplications(JsonNode root) {
    final Map<String, Map<String, String>> groupedResults = new HashMap<>();
    for (JsonNode result : root.path("data").path("result")) {
      String appId = result.path("metric").path(METRICS_APP_ID_TAG).asString();
      String appName = result.path("metric").path(METRICS_APP_NAME_TAG).asString();
      String type = result.path("metric").path("type").asString();
      String value = result.path("value").get(1).asString();
      // combine measurements
      groupedResults.computeIfAbsent(appId, k -> new HashMap<>()).put(type, value);

      // Add appName and appId to the map for this appId
      groupedResults.get(appId).put(METRICS_APP_NAME_TAG, appName);
      groupedResults.get(appId).put(METRICS_APP_ID_TAG, appId);
    }

    return groupedResults.values();
  }

  /**
   * Processes the JSON response from a Prometheus query and groups the results by
   * {@link TagNames#METRICS_APP_LAYER_ID_TAG}. Each appLayerId will have a map of metrics, where the key is the
   * metric type (e.g., "total", "lastUpdated") and the value is the metric value. Additionally, the appName is
   * included in the map for each appId.
   *
   * @param root the root JSON node containing the Prometheus query results (expected structure: root.data.result)
   *     where each result has a "metric" object with "appId", "type", and "value" fields, and a "value" array where
   *     the second element is the metric value. The "metric" object may also contain "appName".
   * @return a map where the key is the appLayerId and the value is another map containing metric types as keys and
   *     their corresponding values. The inner map also includes the appName under the key "appName". If an appId has
   *     no metrics, it will not be included in the results.
   * @see TagNames#METRICS_APP_LAYER_ID_TAG
   */
  public Collection<Map<String, String>> processPrometheusResultsForApplicationLayers(JsonNode root) {
    final Map<String, Map<String, String>> groupedResults = new HashMap<>();
    for (JsonNode result : root.path("data").path("result")) {
      String appLayerId =
          result.path("metric").path(METRICS_APP_LAYER_ID_TAG).asString();
      String appId = result.path("metric").path(METRICS_APP_ID_TAG).asString();
      String appName = result.path("metric").path(METRICS_APP_NAME_TAG).asString();
      String type = result.path("metric").path("type").asString();
      String value = result.path("value").get(1).asString();
      // combine all measurements
      groupedResults.computeIfAbsent(appLayerId, k -> new HashMap<>()).put(type, value);

      // Add appName and appId and appLayerId to the map for this appLayerId
      groupedResults.get(appLayerId).put(METRICS_APP_NAME_TAG, appName);
      groupedResults.get(appLayerId).put(METRICS_APP_ID_TAG, appId);
      groupedResults.get(appLayerId).put(METRICS_APP_LAYER_ID_TAG, appLayerId);
    }

    return groupedResults.values();
  }

  /**
   * Merges two Prometheus JSON responses and processes the results into an ArrayNode. Each appId will have a map of
   * metrics, where the key is the metric type (e.g., "total", "lastUpdated") and the value is the metric value.
   *
   * @param jsonResponse1 the first JSON response from a Prometheus query
   * @param jsonResponse2 the second JSON response from a Prometheus query
   * @return an ArrayNode containing maps of metrics for each appId
   * @throws IOException if there is an error processing the JSON responses
   */
  public ArrayNode processPrometheusResultsToJsonArray(JsonNode jsonResponse1, JsonNode jsonResponse2)
      throws IOException {
    JsonNode mergedResults =
        new ObjectMapper().readerForUpdating(jsonResponse1).readValue(jsonResponse2);
    Collection<Map<String, String>> mergedResultsList = this.processPrometheusResultsForApplications(mergedResults);
    return new ObjectMapper().valueToTree(mergedResultsList);
  }
}
