/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.persistence.projections;

import java.util.List;
import org.springframework.data.rest.core.config.Projection;
import org.tailormap.api.persistence.GeoService;
import org.tailormap.api.persistence.json.AuthorizationRule;
import org.tailormap.api.persistence.json.GeoServiceLayer;
import org.tailormap.api.persistence.json.GeoServiceProtocol;
import org.tailormap.api.persistence.json.GeoServiceSettings;

@Projection(
    name = "summary",
    types = {GeoService.class})
public interface GeoServiceSummary {
  String getId();

  GeoServiceProtocol getProtocol();

  String getTitle();

  List<GeoServiceLayer> getLayers();

  GeoServiceSettings getSettings();

  List<AuthorizationRule> getAuthorizationRules();
}
