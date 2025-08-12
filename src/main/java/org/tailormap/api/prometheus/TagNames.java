/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.prometheus;

/** Interface defining tag names used in Micrometer / Prometheus metrics for the Tailormap API. */
public interface TagNames {
  // Metric names
  String METRICS_APP_REQUEST_COUNTER_NAME = "tailormap_app_request";

  // Tags for the counters
  String METRICS_APP_NAME_TAG = "appName";
  String METRICS_APP_TYPE_TAG = "appType";
  String METRICS_APP_ID_TAG = "appId";
  String METRICS_APP_LAYER_ID_TAG = "appLayerId";
}
