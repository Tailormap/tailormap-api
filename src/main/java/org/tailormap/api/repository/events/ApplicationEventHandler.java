/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.repository.events;

import static org.tailormap.api.prometheus.TagNames.METRICS_APP_ID_TAG;
import static org.tailormap.api.prometheus.TagNames.METRICS_APP_REQUEST_COUNTER_NAME;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.rest.core.annotation.HandleAfterDelete;
import org.springframework.data.rest.core.annotation.RepositoryEventHandler;
import org.springframework.stereotype.Component;
import org.tailormap.api.persistence.Application;
import org.tailormap.api.prometheus.PrometheusService;

@Component
@RepositoryEventHandler
public class ApplicationEventHandler {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final PrometheusService prometheusService;

  @Value("${tailormap-api.allowed-metrics}")
  private Set<String> allowedMetrics;

  public ApplicationEventHandler(PrometheusService prometheusService) {
    this.prometheusService = prometheusService;
  }

  /**
   * Handle after delete. Delete any associated task.
   *
   * @param application the application that was deleted
   */
  @HandleAfterDelete
  public void afterDeleteApplicationEventHandler(Application application) {
    logger.debug("Application '{}' (id: {}) was deleted.", application.getName(), application.getId());
    if (prometheusService.isPrometheusAvailable()) {
      // cleanup any metrics from Prometheus or other systems if needed
      ArrayList<String> metricsToDelete = new ArrayList<>();
      // application metrics
      metricsToDelete.add(METRICS_APP_REQUEST_COUNTER_NAME + "_total{" + METRICS_APP_ID_TAG + "=\""
          + application.getId().toString() + "\"}");
      // application layer metrics
      for (String metricName : allowedMetrics) {
        metricsToDelete.add(metricName + "_total{" + METRICS_APP_ID_TAG + "=\""
            + application.getId().toString() + "\"}");
      }
      try {
        logger.info(
            "Cleaning up all metrics for deleted application with id: {}, (name: '{}')",
            application.getId(),
            application.getName());
        logger.debug("Deleting metrics matching: {}", metricsToDelete);
        prometheusService.deleteMetric(metricsToDelete.toArray(new String[metricsToDelete.size()]));
      } catch (IOException e) {
        logger.error("Error cleaning up metrics for deleted application with id: {}", application.getId(), e);
      }
    }
  }
}
