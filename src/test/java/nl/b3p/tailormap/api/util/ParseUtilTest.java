/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ParseUtilTest {
  private final String rdprojectionCode =
      "EPSG:28992[+proj=sterea +lat_0=52.15616055555555 +lon_0=5.38763888888889 +k=0.9999079 +x_0=155000 +y_0=463000 +ellps=bessel +towgs84=565.237,50.0087,465.658,-0.406857,0.350733,-1.87035,4.0812 +units=m +no_defs]";

  @Test
  void testParseEpsgCode() {
    assertEquals("EPSG:28992", ParseUtil.parseEpsgCode(rdprojectionCode), "parsing failed");
  }

  @Test
  void testParseProjDefintion() {
    assertEquals(
        "+proj=sterea +lat_0=52.15616055555555 +lon_0=5.38763888888889 +k=0.9999079 +x_0=155000 +y_0=463000 +ellps=bessel +towgs84=565.237,50.0087,465.658,-0.406857,0.350733,-1.87035,4.0812 +units=m +no_defs",
        ParseUtil.parseProjDefintion(rdprojectionCode),
        "parsing failed");
  }
}
