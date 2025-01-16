/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.persistence.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.tailormap.api.annotation.PostgresIntegrationTest;
import org.tailormap.api.persistence.GeoService;
import org.tailormap.api.persistence.json.GeoServiceLayer;
import org.tailormap.api.repository.GeoServiceRepository;

@PostgresIntegrationTest
class GeoServiceHelperIntegrationTest {

  @Autowired
  private GeoServiceRepository geoServiceRepository;

  @Test
  void testGetLayerLegendUrlFromStyles() {
    GeoService service =
        geoServiceRepository.findById("demo").stream().findFirst().orElseThrow();
    GeoServiceLayer layer = service.findLayer("geomorfologie");
    String expected =
        "https://demo.tailormap.com/geoserver/geodata/ows?service=WMS&version=1.3.0&request=GetLegendGraphic&format=image%2Fpng&width=20&height=20&layer=geomorfologie";
    String actual =
        GeoServiceHelper.getLayerLegendUrlFromStyles(service, layer).toString();
    assertEquals(expected, actual, "Expected and actual legend url are not equal");
  }
}
