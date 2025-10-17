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
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Service for managing Prometheus-related operations. This service can be used to manage Prometheus-related operations
 * such as querying and managing metrics stored in the Prometheus instance.
 */
@Component
public class PrometheusService {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Value("${tailormap-api.prometheus-api-url}")
  private String prometheusUrl;

  private final RestTemplate restTemplate;

  public PrometheusService(RestTemplate template) {
    this.restTemplate = template;
  }

  /** Check if the Prometheus server is available by sending a simple query. */
  public boolean isPrometheusAvailable() {
    try {
      ResponseEntity<String> response =
          restTemplate.getForEntity(prometheusUrl + "/query?query=up", String.class);
      if (response.getStatusCode() == HttpStatus.OK) {
        return true;
      } else {
        logger.warn("Prometheus server is not available: {}", response.getStatusCode());
        return false;
      }
    } catch (Exception e) {
      logger.debug("Error checking Prometheus availability", e);
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
    ResponseEntity<String> response;
    URI promUrl = UriComponentsBuilder.fromUriString(prometheusUrl)
        .path("/query")
        .queryParam("query", promQuery)
        .build()
        .toUri();
    logger.trace("Executing Prometheus query (GET): {}", promUrl);
    try {
      response = restTemplate.getForEntity(promUrl, String.class);
      if (response.getStatusCode() != HttpStatus.OK) {
        logger.error("Failed to execute Prometheus query: {}", response.getStatusCode());
        throw new IOException("Failed to execute Prometheus query: " + response.getStatusCode());
      }
    } catch (RestClientException e) {
      logger.error("Error executing Prometheus query: {}", e.getMessage());
      throw new IOException("Error executing Prometheus query: " + e.getMessage(), e);
    }

    final JsonNode jsonResponse = new ObjectMapper().readTree(response.getBody());
    logger.trace("Prometheus query response: {}", jsonResponse.toPrettyString());

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
   * @param metricMatches the Prometheus metric matches to be deleted
   * @throws IOException if there is an error executing the delete query
   */
  public void deleteMetric(String... metricMatches) throws IOException, URISyntaxException {
    final URL url = UriComponentsBuilder.fromUriString(prometheusUrl)
        .path("/admin/tsdb/delete_series")
        .queryParam("match[]", (Object[]) metricMatches)
        .build()
        .toUri()
        .toURL();
    logger.trace("Deleting metrics using (PUT): {}", url);
    HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
    httpURLConnection.setRequestMethod("PUT");

    if (httpURLConnection.getResponseCode() != HttpStatus.NO_CONTENT.value()) {
      throw new IOException("Failed to delete Prometheus metric: " + httpURLConnection.getResponseCode());
    }
  }

  /**
   * Executes a Prometheus tombstone query.
   *
   * @throws IOException if there is an error executing the delete query
   */
  public void cleanTombstones() throws IOException, URISyntaxException {
    final URL url = new URI(prometheusUrl + "/admin/tsdb/clean_tombstones").toURL();
    HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
    httpURLConnection.setRequestMethod("PUT");

    if (httpURLConnection.getResponseCode() != HttpStatus.NO_CONTENT.value()) {
      throw new IOException("Failed to cleanup Prometheus tombstones: " + httpURLConnection.getResponseCode());
    }
  }
}
