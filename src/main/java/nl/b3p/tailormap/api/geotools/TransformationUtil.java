/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.geotools;

import jakarta.validation.constraints.NotNull;
import nl.b3p.tailormap.api.persistence.Application;
import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.api.referencing.FactoryException;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.referencing.CRS;

public class TransformationUtil {
  private TransformationUtil() {
    // utility class
  }

  /**
   * Determine whether we need to transform geometries to the application CRS. Note that this uses
   * the "default geometry" attribute of the feature source, in cases where a feature source has
   * multiple geometry attributes (with possibly different CRSs) or heterogenous CRSs across a
   * single geometry attribute this may not be accurate.
   *
   * @param application the referenced application
   * @param simpleFeatureSource the feature source used in the application
   * @return {@code null} when no transform is required and a valid transform otherwise
   * @throws FactoryException when the CRS cannot be decoded
   */
  public static MathTransform getTransformationToApplication(
      @NotNull Application application, @NotNull SimpleFeatureSource simpleFeatureSource)
      throws FactoryException {
    MathTransform transform = null;
    // this is the CRS of the "default geometry" attribute
    final CoordinateReferenceSystem dataSourceCRS =
        simpleFeatureSource.getSchema().getCoordinateReferenceSystem();
    final CoordinateReferenceSystem appCRS = CRS.decode(application.getCrs());
    if (!CRS.equalsIgnoreMetadata(dataSourceCRS, appCRS)) {
      transform = CRS.findMathTransform(dataSourceCRS, appCRS);
    }
    return transform;
  }

  /**
   * Determine whether we need to transform geometry to data source crs. Note that this uses the
   * "default geometry" attribute of the feature source, in cases where a feature source has
   * multiple geometry attributes (with possibly different CRSs) or heterogenous CRSs across a
   * single geometry attribute this may not be accurate.
   *
   * @param application the referenced application
   * @param simpleFeatureSource the feature source used in the application
   * @return {@code null} when no transform is required and a valid transform otherwise
   * @throws FactoryException when the CRS cannot be decoded
   */
  public static MathTransform getTransformationToDataSource(
      @NotNull Application application, @NotNull SimpleFeatureSource simpleFeatureSource)
      throws FactoryException {
    MathTransform transform = null;
    // this is the CRS of the "default geometry" attribute
    final CoordinateReferenceSystem dataSourceCRS =
        simpleFeatureSource.getSchema().getCoordinateReferenceSystem();
    final CoordinateReferenceSystem appCRS = CRS.decode(application.getCrs());
    if (!CRS.equalsIgnoreMetadata(dataSourceCRS, appCRS)) {
      transform = CRS.findMathTransform(appCRS, dataSourceCRS);
    }
    return transform;
  }
}
