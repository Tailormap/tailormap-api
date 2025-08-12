/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller;

import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Metrics;
import jakarta.validation.Valid;
import java.io.Serializable;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.tailormap.api.annotation.AppRestController;
import org.tailormap.api.persistence.Application;
import org.tailormap.api.viewer.model.ViewerResponse;

@AppRestController
@Validated
public class IngestMetricsController {

  @PutMapping(
      path = {
        "${tailormap-api.base-path}/app/{viewerName}/metrics/ingest/{appLayerId}/switched-on",
        "${tailormap-api.base-path}/service/{viewerName}/metrics/ingest/{appLayerId}/switched-on"
      })
  @Valid public ResponseEntity<Serializable> layerSwitchedOn(
      @ModelAttribute Application app,
      @ModelAttribute ViewerResponse.KindEnum viewerKind,
      @NonNull @PathVariable String appLayerId) {

    // TODO  check if the appLayerId is valid, i.e. exists in the application

    // count/increment the number of times this layer has been switched on for this viewer
    Metrics.counter(
            "tailormap_applayer_switched_on",
            "appId",
            app.getId().toString(),
            "appLayerId",
            appLayerId,
            "appType",
            viewerKind.name().toLowerCase(Locale.getDefault()),
            "appName",
            app.getName(),
            "appLayerId",
            appLayerId)
        .increment();

    // Return a 204 No Content response, to indicate that the request was successful but there is no content to
    // return.
    return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
  }
}
