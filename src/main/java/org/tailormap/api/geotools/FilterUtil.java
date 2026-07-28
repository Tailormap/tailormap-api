/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.geotools;

import java.lang.invoke.MethodHandles;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.api.filter.Filter;
import org.geotools.api.referencing.FactoryException;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.filter.text.cql2.CQLException;
import org.geotools.filter.text.ecql.ECQL;
import org.geotools.referencing.CRS;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tailormap.api.persistence.Application;

public class FilterUtil {
  private static final Pattern sridPattern = Pattern.compile("(?i)SRID=\\d+;");
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private FilterUtil() {
    /* utility class */
  }

  /**
   * Parses a CQL filter string into a GeoTools Filter, transforming geometries to the data source CRS if
   * needed/possible.
   *
   * @param filterCQL the CQL filter string
   * @param application the application context
   * @param featureSource the feature source
   * @return the parsed filter, with geometries transformed to the data source CRS if needed
   * @throws CQLException if the filter cannot be parsed
   * @throws FactoryException if there is an error with the CRS
   * @throws UnsupportedOperationException if the filter SRID does not match the application CRS or different SRID
   *     appear in the filter
   */
  @NonNull public static Filter parseFilter(
      @NonNull String filterCQL, @NonNull Application application, @NonNull SimpleFeatureSource featureSource)
      throws CQLException, FactoryException, UnsupportedOperationException {
    @SuppressWarnings("PMD.UseFilterUtil")
    Filter filter = ECQL.toFilter(filterCQL);
    // if the filter is spatial/has EWKT geometries and the SRID is different from the data source
    // we need to transform the geometries to the data source CRS
    String foundSrid = null;
    final Matcher sridMatcher = sridPattern.matcher(filterCQL);
    while (sridMatcher.find()) {
      // only support the EPSG authority for now, so we replace the SRID= with EPSG: and remove the trailing ;
      String srid = sridMatcher.group().replaceAll("(?i)SRID=", "EPSG:").replace(";", "");
      if (foundSrid == null) {
        foundSrid = srid;
      } else if (!foundSrid.equalsIgnoreCase(srid)) {
        throw new UnsupportedOperationException("All SRIDs in filter must be identical");
      }
    }
    if (foundSrid != null) {
      logger.trace("Filter contains SRID, checking if transformation is needed");
      CoordinateReferenceSystem dataSourceCRS = featureSource.getSchema().getCoordinateReferenceSystem();
      CoordinateReferenceSystem filterCRS = CRS.decode(foundSrid);
      CoordinateReferenceSystem appCRS = CRS.decode(application.getCrs());

      if (!CRS.isEquivalent(appCRS, filterCRS)) {
        // only support application CRS input
        throw new UnsupportedOperationException("Filter SRID does not match application CRS");
      }

      if (!CRS.isEquivalent(dataSourceCRS, filterCRS)) {
        MathTransform transform = TransformationUtil.getTransformationToDataSource(application, featureSource);
        if (transform != null) {
          filter = TransformationUtil.transformFilterGeometries(filter, transform);
        }
      }
    }
    return filter;
  }
}
