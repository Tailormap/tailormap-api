/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.prometheus;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.tailormap.api.annotation.PostgresIntegrationTest;

@PostgresIntegrationTest
@Order(Integer.MAX_VALUE)
@TestPropertySource(
    properties = {
      "tailormap-api.prometheus-api-url=http://localhost:9091/unhappy-path/",
    })
class PrometheusServiceUnhappyIntegrationTest {
  @Autowired
  private PrometheusService prometheusService;

  @Test
  void isPrometheusAvailable() {
    boolean isAvailable = prometheusService.isPrometheusAvailable();
    assertFalse(isAvailable, "Prometheus server should not be available");
  }

  @Test
  void testQueryExecution() {
    Exception exception = assertThrows(IOException.class, () -> {
      prometheusService.executeQuery("scrape_duration_seconds");
    });
    assertThat(exception.getMessage(), containsStringIgnoringCase("Connection refused"));
  }

  @Test
  void deleteMetric() {
    Exception exception = assertThrows(IOException.class, () -> {
      prometheusService.deleteMetric("scrape_samples_scraped");
    });
    assertThat(exception.getMessage(), containsStringIgnoringCase("Connection refused"));
  }

  @Test
  void cleanTombstones() {
    Exception exception = assertThrows(IOException.class, () -> {
      prometheusService.cleanTombstones();
    });
    assertThat(exception.getMessage(), containsStringIgnoringCase("Connection refused"));
  }
}
