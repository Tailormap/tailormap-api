/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package nl.b3p.tailormap.api.persistence.projections;

import java.util.List;
import nl.b3p.tailormap.api.persistence.GeoService;
import nl.b3p.tailormap.api.persistence.json.AuthorizationRule;
import nl.b3p.tailormap.api.persistence.json.GeoServiceLayer;
import nl.b3p.tailormap.api.persistence.json.GeoServiceProtocol;
import nl.b3p.tailormap.api.persistence.json.GeoServiceSettings;
import org.springframework.data.rest.core.config.Projection;

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
