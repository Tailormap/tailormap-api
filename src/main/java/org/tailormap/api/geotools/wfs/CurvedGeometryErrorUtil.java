/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.geotools.wfs;

import org.tailormap.api.persistence.TMFeatureType;

/**
 * Recognizes the error GeoTools throws when a WFS returns a geometry with circular arcs that it can't parse.
 *
 * <p>The GML 3.1.1 parser that GeoTools uses for WFS 1.1.0 does not support curve segments such as
 * {@code gml:ArcString}, and throws an {@link UnsupportedOperationException} ("Curve segment ArcString is not supported
 * yet") while iterating the features. GeoServer outputs these for layers with circular arcs, for instance from a
 * PostGIS or Oracle table. The parser GeoTools uses for WFS 2.0.0 linearizes them, so the fix is to use WFS 2.0.0 (no
 * {@code VERSION=1.1.0} in the feature source URL).
 */
public final class CurvedGeometryErrorUtil {
  private static final String CURVE_SEGMENT_MESSAGE_PREFIX = "Curve segment ";

  private CurvedGeometryErrorUtil() {
    // utility class
  }

  /**
   * Check whether the throwable or one of its causes is the GeoTools error for an unsupported curve segment.
   *
   * @param throwable the exception thrown while reading features
   * @return {@code true} if the features could not be read because of a curved geometry
   */
  public static boolean isCurvedGeometryError(Throwable throwable) {
    // Limit the depth in case of a cause cycle
    int depth = 0;
    for (Throwable t = throwable; t != null && depth < 10; t = t.getCause(), depth++) {
      if (t instanceof UnsupportedOperationException
          && t.getMessage() != null
          && t.getMessage().startsWith(CURVE_SEGMENT_MESSAGE_PREFIX)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Create a message for a user or administrator explaining why features could not be read.
   *
   * @param featureType the feature type that was read
   * @return the message
   */
  public static String getCurvedGeometryErrorMessage(TMFeatureType featureType) {
    String name = featureType.getName();
    String source = featureType.getFeatureSource() != null
        ? featureType.getFeatureSource().getTitle()
        : null;
    return "Cannot read features of feature type \"%s\"%s: the WFS returned a geometry with circular arcs, which"
            .formatted(name, source != null ? " from feature source \"%s\"".formatted(source) : "")
        + " is not supported with WFS version 1.1.0. Remove VERSION=1.1.0 from the feature source URL to use"
        + " WFS 2.0.0.";
  }
}
