/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller;

import io.micrometer.core.instrument.Metrics;
import io.swagger.v3.oas.annotations.Operation;
import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.tailormap.api.annotation.AppRestController;
import org.tailormap.api.persistence.Application;
import org.tailormap.api.prometheus.TagNames;
import org.tailormap.api.viewer.model.ViewerResponse;

@AppRestController
@Validated
public class IngestMetricsController implements TagNames {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Value("${tailormap-api.allowed-metrics}")
  private Set<String> allowedMetrics;

  /**
   * Ingests a metric for the given application layers and viewer kind.
   *
   * @param app the application
   * @param viewerKind the kind of viewer
   * @param appLayerIds the application layer IDs to ingest the metric for
   * @param allowedMetric the metric to ingest
   * @return a ResponseEntity with status 204 No Content if successful, or 400 Bad Request if the metric is not
   *     allowed
   */
  @Operation(
      summary = "Ingest a metric for the given application layer(s)",
      description =
          """
Ingests a metric for the given application layer(s), the metric will be published through the actuator endpoint as
a Micrometer counter. All application layers in the appLayerIds that are part of the application will be processed.
The metric must be one of the allowed metrics defined in the application configuration.""")
  @PutMapping(
      path = "${tailormap-api.base-path}/{viewerKind}/{viewerName}/metrics/ingest/{appLayerIds}/{allowedMetric}")
  public ResponseEntity<Serializable> ingestMetric(
      @ModelAttribute Application app,
      @ModelAttribute ViewerResponse.KindEnum viewerKind,
      @PathVariable Set<String> appLayerIds,
      @PathVariable String allowedMetric) {

    if (!allowedMetrics.contains(allowedMetric)) {
      logger.warn("Invalid metric: {}, not in allowed metrics: {}", allowedMetric, allowedMetrics);
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }
    if (appLayerIds.isEmpty()) {
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }
    logger.trace("Ingesting metric for {} layer(s)", appLayerIds);

    // Validate that the given application layers in appLayerIds are actually part of this application
    app.getAllAppTreeLayerNode()
        .filter(appTreeLayerNode -> appLayerIds.contains(appTreeLayerNode.getId()))
        // for each valid appLayer publish metrics
        .forEach(appTreeLayerNode -> {
          logger.debug(
              "Ingesting metric: {}, appId={}, viewerKind={}, appLayerId={} to be published through the actuator endpoint",
              allowedMetric,
              app.getId(),
              viewerKind,
              appTreeLayerNode.getId());

          // count/increment the number of times this layer has been switched on for this viewer
          Metrics.counter(
                  allowedMetric,
                  METRICS_APP_ID_TAG,
                  app.getId().toString(),
                  METRICS_APP_TYPE_TAG,
                  viewerKind.name().toLowerCase(Locale.getDefault()),
                  METRICS_APP_NAME_TAG,
                  app.getName(),
                  METRICS_APP_LAYER_ID_TAG,
                  appTreeLayerNode.getId())
              // we don't add the appLayerName here, as in many cases you want the allLayerTitle, but that
              // requires an extra lookup in the appLayerSettings when publishing the metric and does not
              // take
              // renaming into account, that can better be done when rendering the metrics
              .increment();
        });
    // Return a 204 No Content response, to indicate that the request was successful but there is no content to
    // return.
    return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
  }
}
