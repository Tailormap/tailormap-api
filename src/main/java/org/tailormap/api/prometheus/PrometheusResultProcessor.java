/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.prometheus;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PrometheusResultProcessor implements TagNames {

  /**
   * Processes the JSON response from a Prometheus query and groups the results by appId. Each appId will have a map
   * of metrics, where the key is the metric type (e.g., "total", "lastUpdated") and the value is the metric value.
   * Additionally, the appName is included in the map for each appId.
   *
   * @param root the root JSON node containing the Prometheus query results (expected structure: root.data.result)
   *     where each result has a "metric" object with "appId", "type", and "value" fields, and a "value" array where
   *     the second element is the metric value. The "metric" object may also contain "appName".
   * @return a map where the key is the appId and the value is another map containing metric types as keys and their
   *     corresponding values. The inner map also includes the appName under the key "appName". If an appId has no
   *     metrics, it will not be included in the results.
   */
  public Map<String, Map<String, String>> processPrometheusResults(JsonNode root) {
    final Map<String, Map<String, String>> groupedResults = new HashMap<>();
    for (JsonNode result : root.path("data").path("result")) {
      String appId = result.path("metric").path(METRICS_APP_ID_TAG).asText();
      String type = result.path("metric").path("type").asText();
      String value = result.path("value").get(1).asText();

      groupedResults.computeIfAbsent(appId, k -> new HashMap<>()).put(type, value);

      String appName = result.path("metric").path(METRICS_APP_NAME_TAG).asText();
      groupedResults.get(appId).put(METRICS_APP_NAME_TAG, appName);
    }

    return groupedResults;
  }

  /**
   * Processes the JSON response from a Prometheus query and groups the results by appId. This method is a convenience
   * wrapper around {@link #processPrometheusResults(JsonNode)}.
   *
   * @param jsonResponse the JSON response string from a Prometheus query
   * @return a map where the key is the appId and the value is another map containing metric types as keys and their
   *     corresponding values.
   * @throws JsonProcessingException if there is an error parsing the JSON response
   */
  public Map<String, Map<String, String>> processPrometheusResults(String jsonResponse)
      throws JsonProcessingException {
    ObjectMapper objectMapper = new ObjectMapper();
    JsonNode root = objectMapper.readTree(jsonResponse);
    return this.processPrometheusResults(root);
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
    Map<String, Map<String, String>> processed = this.processPrometheusResults(mergedResults);
    for (String key : processed.keySet()) {
      processed.get(key).put(METRICS_APP_ID_TAG, key);
    }
    List<Map<String, String>> mergedResultsList = new ArrayList<>(processed.values());
    return new ObjectMapper().valueToTree(mergedResultsList);
  }
}
