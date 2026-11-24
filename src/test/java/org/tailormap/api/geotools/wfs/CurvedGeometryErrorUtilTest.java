/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.geotools.wfs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.tailormap.api.persistence.TMFeatureSource;
import org.tailormap.api.persistence.TMFeatureType;

class CurvedGeometryErrorUtilTest {
  private static final String GEOTOOLS_MESSAGE = "Curve segment ArcString is not supported yet";

  @Test
  void recognizes_curve_segment_error() {
    assertTrue(CurvedGeometryErrorUtil.isCurvedGeometryError(new UnsupportedOperationException(GEOTOOLS_MESSAGE)));
  }

  @Test
  void recognizes_wrapped_curve_segment_error() {
    Exception wrapped = new RuntimeException(
        new IOException("parse error", new UnsupportedOperationException(GEOTOOLS_MESSAGE)));
    assertTrue(CurvedGeometryErrorUtil.isCurvedGeometryError(wrapped));
  }

  @Test
  void ignores_other_errors() {
    assertFalse(
        CurvedGeometryErrorUtil.isCurvedGeometryError(new UnsupportedOperationException("Not implemented")));
    assertFalse(CurvedGeometryErrorUtil.isCurvedGeometryError(new UnsupportedOperationException()));
    assertFalse(CurvedGeometryErrorUtil.isCurvedGeometryError(new IllegalStateException(GEOTOOLS_MESSAGE)));
    assertFalse(CurvedGeometryErrorUtil.isCurvedGeometryError(null));
  }

  @Test
  void message_names_feature_type_and_source() {
    TMFeatureType featureType = new TMFeatureType()
        .setName("postgis:begroeidterreindeel")
        .setFeatureSource(new TMFeatureSource().setTitle("Test GeoServer (WFS 1.1.0)"));
    assertEquals(
        "Cannot read features of feature type \"postgis:begroeidterreindeel\" from feature source \"Test GeoServer"
            + " (WFS 1.1.0)\": the WFS returned a geometry with circular arcs, which is not supported with WFS"
            + " version 1.1.0. Remove VERSION=1.1.0 from the feature source URL to use WFS 2.0.0.",
        CurvedGeometryErrorUtil.getCurvedGeometryErrorMessage(featureType));
  }
}
