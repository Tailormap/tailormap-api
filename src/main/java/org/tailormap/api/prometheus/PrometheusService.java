/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.prometheus;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * Service for managing Prometheus-related operations. This service can be used to manage Prometheus-related operations
 * such as querying and managing metrics stored in the Prometheus instance.
 */
@Service
public class PrometheusService {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Value("${tailormap-api.prometheus-api-url}")
  private String prometheusUrl;

  /** Check if the Prometheus server is available by sending a simple query. */
  public boolean isPrometheusAvailable() {
    RestTemplate restTemplate = new RestTemplate();
    try {
      ResponseEntity<String> response =
          restTemplate.getForEntity(prometheusUrl + "/query?query=up", String.class);
      if (response.getStatusCode() == HttpStatus.OK) {
        return true;
      } else {
        logger.error("Prometheus server is not available: {}", response.getStatusCode());
        return false;
      }
    } catch (Exception e) {
      logger.error("Error checking Prometheus availability", e);
      return false;
    }
  }
  /**
   * Executes a Prometheus query and returns the result.
   *
   * @param promQuery the Prometheus query to execute
   * @return the result of the query as a JSON node
   * @throws JsonProcessingException if there is an error processing the JSON response
   * @throws IOException if there is an error executing the query
   * @see PrometheusResultProcessor
   */
  public JsonNode executeQuery(String promQuery) throws JsonProcessingException, IOException {
    RestTemplate restTemplate = new RestTemplate();
    ResponseEntity<String> response;
    String promUrl = prometheusUrl + "/query?query=" + promQuery;
    logger.debug("Executing Prometheus query: {}", promUrl);
    try {
      response = restTemplate.getForEntity(promUrl, String.class);
    } catch (HttpClientErrorException | HttpServerErrorException e) {
      logger.error("Error executing Prometheus query: {}", e.getMessage());
      throw new IOException("Error executing Prometheus query: " + e.getMessage(), e);
    }
    if (response.getStatusCode() != HttpStatus.OK) {
      logger.error("Failed to execute Prometheus query: {}", response.getStatusCode());
      throw new IOException("Failed to execute Prometheus query: " + response.getStatusCode());
    }

    final JsonNode jsonResponse = new ObjectMapper().readTree(response.getBody());
    logger.debug("Prometheus query response: {}", jsonResponse.toPrettyString());

    if (!"success".equals(jsonResponse.path("status").asText())) {
      logger.error(
          "Prometheus query failed: {}", jsonResponse.path("error").asText());
      throw new IOException(
          "Prometheus query failed: " + jsonResponse.path("error").asText());
    }
    return jsonResponse;
  }

  /**
   * Executes a Prometheus delete query.
   *
   * @param metricName the Prometheus metric to be deleted
   * @throws IOException if there is an error executing the delete query
   */
  public void deleteMetric(String metricName) throws IOException {
    RestTemplate restTemplate = new RestTemplate();
    ResponseEntity<String> response = restTemplate.exchange(
        prometheusUrl + "/admin/tsdb/delete_series?match[]=" + metricName, HttpMethod.PUT, null, String.class);

    if (response.getStatusCode() != HttpStatus.NO_CONTENT) {
      logger.error("Failed to delete Prometheus metric: {}", response.getStatusCode());
      throw new IOException("Failed to delete Prometheus metric: " + response.getStatusCode());
    }
  }

  /**
   * Executes a Prometheus tombstone query.
   *
   * @throws IOException if there is an error executing the delete query
   */
  public void cleanTombstones() throws IOException {
    RestTemplate restTemplate = new RestTemplate();
    ResponseEntity<String> response = restTemplate.exchange(
        prometheusUrl + "/admin/tsdb/clean_tombstones", HttpMethod.PUT, null, String.class);

    if (response.getStatusCode() != HttpStatus.NO_CONTENT) {
      logger.error("Failed to cleanup Prometheus tombstones: {}", response.getStatusCode());
      throw new IOException("Failed to cleanup Prometheus tombstones: " + response.getStatusCode());
    }
  }
}
