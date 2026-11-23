/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.prometheus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

class PrometheusServiceTest {
  private static final String PROMETHEUS_URL = "http://prometheus.example";

  private MockRestServiceServer mockServer;
  private PrometheusService prometheusService;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder();
    mockServer = MockRestServiceServer.bindTo(builder).build();
    prometheusService = new PrometheusService(builder);
    ReflectionTestUtils.setField(prometheusService, "prometheusUrl", PROMETHEUS_URL);
  }

  @Test
  void isPrometheusAvailableReturnsTrueForSuccessfulResponse() {
    mockServer
        .expect(requestTo(PROMETHEUS_URL + "/query?query=up"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess("{\"status\":\"success\"}", MediaType.APPLICATION_JSON));

    assertTrue(prometheusService.isPrometheusAvailable());

    mockServer.verify();
  }

  @Test
  void isPrometheusAvailableReturnsFalseForHttpError() {
    mockServer
        .expect(requestTo(PROMETHEUS_URL + "/query?query=up"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

    assertFalse(prometheusService.isPrometheusAvailable());

    mockServer.verify();
  }

  @Test
  void executeQueryReturnsSuccessfulResponse() throws IOException {
    mockServer
        .expect(requestTo(PROMETHEUS_URL + "/query?query=up"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess("{\"status\":\"success\",\"data\":{\"result\":[]}}", MediaType.APPLICATION_JSON));

    JsonNode response = prometheusService.executeQuery("up");

    assertEquals("success", response.path("status").asString());
    assertTrue(response.path("data").path("result").isArray());
    mockServer.verify();
  }

  @Test
  void executeQueryThrowsIOExceptionForPrometheusError() {
    mockServer
        .expect(requestTo(PROMETHEUS_URL + "/query?query=up"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess("{\"status\":\"error\",\"error\":\"bad query\"}", MediaType.APPLICATION_JSON));

    IOException exception = assertThrows(IOException.class, () -> prometheusService.executeQuery("up"));

    assertTrue(exception.getMessage().contains("bad query"));
    mockServer.verify();
  }

  @Test
  void executeQueryThrowsIOExceptionForHttpError() {
    mockServer
        .expect(requestTo(PROMETHEUS_URL + "/query?query=up"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

    IOException exception = assertThrows(IOException.class, () -> prometheusService.executeQuery("up"));

    assertTrue(exception.getMessage().contains("Error executing Prometheus query"));
    assertInstanceOf(RestClientException.class, exception.getCause());
    mockServer.verify();
  }
}
