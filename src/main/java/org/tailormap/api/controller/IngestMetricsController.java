/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller;

import io.micrometer.core.instrument.Metrics;
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
import org.tailormap.api.persistence.json.AppTreeLayerNode;
import org.tailormap.api.viewer.model.ViewerResponse;

@AppRestController
@Validated
public class IngestMetricsController {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Value("${tailormap-api.allowed-metrics}")
  private Set<String> allowedMetrics;

  @PutMapping(
      path = "${tailormap-api.base-path}/{viewerKind}/{viewerName}/metrics/ingest/{appLayerId}/{allowedMetric}")
  public ResponseEntity<Serializable> ingestMetric(
      @ModelAttribute Application app,
      @ModelAttribute ViewerResponse.KindEnum viewerKind,
      @ModelAttribute AppTreeLayerNode appTreeLayerNode,
      @PathVariable String allowedMetric) {

    if (!allowedMetrics.contains(allowedMetric)) {
      logger.warn("Invalid metric: {}, not in allowed metrics: {}", allowedMetric, allowedMetrics);
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }

    logger.debug(
        "Ingesting metric: {}, appId={}, viewerKind={}, appLayerId={} to be published through the actuator endpoint",
        allowedMetric,
        app.getId(),
        viewerKind,
        appTreeLayerNode.getId());

    // count/increment the number of times this layer has been switched on for this viewer
    Metrics.counter(
            allowedMetric,
            "appId",
            app.getId().toString(),
            "appType",
            viewerKind.name().toLowerCase(Locale.getDefault()),
            "appName",
            app.getName(),
            "appLayerId",
            appTreeLayerNode.getId())
        .increment();

    // Return a 204 No Content response, to indicate that the request was successful but there is no content to
    // return.
    return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
  }
}
