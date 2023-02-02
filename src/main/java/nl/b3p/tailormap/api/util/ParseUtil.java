/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.util;

public final class ParseUtil {

  /**
   * get authority string.
   *
   * @param projectionCode like {@code EPSG:28992[+proj=sterea +lat_0=52.15616055555555
   *     +lon_0=5.38763888888889 +k=0.9999079 +x_0=155000 +y_0=463000 +ellps=bessel
   *     +towgs84=565.237,50.0087,465.658,-0.406857,0.350733,-1.87035,4.0812 +units=m +no_defs]}
   * @return EPSG code (part before first [) eg. {@code EPSG:28992}
   */
  public static String parseEpsgCode(final String projectionCode) {
    return projectionCode.substring(0, projectionCode.indexOf('['));
  }

  /**
   * get proj4j string.
   *
   * @param projectionCode like {@code EPSG:28992[+proj=sterea +lat_0=52.15616055555555
   *     +lon_0=5.38763888888889 +k=0.9999079 +x_0=155000 +y_0=463000 +ellps=bessel
   *     +towgs84=565.237,50.0087,465.658,-0.406857,0.350733,-1.87035,4.0812 +units=m +no_defs]}
   * @return proj4j string (part between [ ..]) eg. {@code +proj=sterea +lat_0=52.15616055555555
   *     +lon_0=5.38763888888889 +k=0.9999079 +x_0=155000 +y_0=463000 +ellps=bessel
   *     +towgs84=565.237,50.0087,465.658,-0.406857,0.350733,-1.87035,4.0812 +units=m +no_defs}
   */
  public static String parseProjDefintion(final String projectionCode) {
    return projectionCode.substring(
        projectionCode.indexOf('[') + 1, projectionCode.lastIndexOf(']'));
  }
}
