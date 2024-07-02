/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.persistence.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import nl.b3p.tailormap.api.annotation.PostgresIntegrationTest;
import nl.b3p.tailormap.api.persistence.GeoService;
import nl.b3p.tailormap.api.persistence.json.GeoServiceLayer;
import nl.b3p.tailormap.api.repository.GeoServiceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@PostgresIntegrationTest
class GeoServiceHelperIntegrationTest {

  @Autowired private GeoServiceRepository geoServiceRepository;

  @Test
  void testGetLayerLegendUrlFromStyles() {
    GeoService service = geoServiceRepository.findById("demo").stream().findFirst().orElseThrow();
    GeoServiceLayer layer = service.findLayer("geomorfologie");
    String expected =
        "https://demo.tailormap.com/geoserver/geodata/ows?service=WMS&version=1.3.0&request=GetLegendGraphic&format=image%2Fpng&width=20&height=20&layer=geomorfologie";
    String actual = GeoServiceHelper.getLayerLegendUrlFromStyles(service, layer).toString();
    assertEquals(expected, actual, "Expected and actual legend url are not equal");
  }
}
