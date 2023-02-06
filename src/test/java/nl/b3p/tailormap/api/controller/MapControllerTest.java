package nl.b3p.tailormap.api.controller;

import static org.junit.jupiter.api.Assertions.*;

import nl.b3p.tailormap.api.StaticTestData;
import nl.b3p.tailormap.api.persistence.Application;
import nl.b3p.tailormap.api.viewer.model.Bounds;
import nl.b3p.tailormap.api.viewer.model.CoordinateReferenceSystem;
import nl.b3p.tailormap.api.viewer.model.MapResponse;
import org.junit.jupiter.api.Test;

/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

class MapControllerTest {
  @Test
  void testAppWithCrsRD() {
    Application a = new Application().setCrs("EPSG:28992");
    MapResponse mr = new MapResponse();
    MapController.getApplicationParams(a, mr);
    CoordinateReferenceSystem crs = mr.getCrs();
    assertNotNull(crs);
    assertEquals("EPSG:28992", crs.getCode());
    assertEquals(StaticTestData.get("RDCrsWkt"), crs.getDefinition());
    assertEquals("m", crs.getUnit());

    // initial and max extent should be the CRS envelope
    assertEquals(a.getStartExtent(), a.getMaxExtent());

    Bounds b = mr.getInitialExtent();
    assertEquals("EPSG:28992", b.getCrs());

    assertEquals(284300.0254094796, b.getMaxx());
    assertEquals(636981.7698870874, b.getMaxy());
    assertEquals(634.5732789819012, b.getMinx());
    assertEquals(306594.5543000576, b.getMiny());
  }

  @Test
  void testAppWithCrs3857() {
    Application a = new Application().setCrs("EPSG:3857");
    MapResponse mr = new MapResponse();
    MapController.getApplicationParams(a, mr);
    CoordinateReferenceSystem crs = mr.getCrs();
    assertNotNull(crs);
    assertEquals("EPSG:3857", crs.getCode());
    assertEquals(StaticTestData.get("WebMercatorCrsWkt"), crs.getDefinition());
    assertEquals("m", crs.getUnit());

    // initial and max extent should be the CRS envelope
    assertEquals(a.getStartExtent(), a.getMaxExtent());

    Bounds b = mr.getInitialExtent();
    assertEquals("EPSG:3857", b.getCrs());

    assertEquals(20037508.342789244, b.getMaxx());
    assertEquals(20048966.104014594, b.getMaxy());
    assertEquals(-20037508.342789244, b.getMinx());
    assertEquals(-20048966.1040146, b.getMiny());
  }
}
