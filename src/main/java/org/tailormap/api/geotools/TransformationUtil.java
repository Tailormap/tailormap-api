/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.geotools;

import java.lang.invoke.MethodHandles;
import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.api.filter.Filter;
import org.geotools.api.filter.FilterVisitor;
import org.geotools.api.filter.spatial.Intersects;
import org.geotools.api.referencing.FactoryException;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.api.referencing.operation.TransformException;
import org.geotools.filter.LiteralExpressionImpl;
import org.geotools.filter.spatial.IntersectsImpl;
import org.geotools.filter.visitor.DefaultFilterVisitor;
import org.geotools.geometry.jts.JTS;
import org.geotools.geometry.jts.WKTReader2;
import org.geotools.referencing.CRS;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tailormap.api.persistence.Application;

public class TransformationUtil {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final WKTReader2 wktReader = new WKTReader2();

  private TransformationUtil() {
    // utility class
  }

  /**
   * Determine whether we need to transform geometries to the application CRS. Note that this uses the "default
   * geometry" attribute of the feature source, in cases where a feature source has multiple geometry attributes (with
   * possibly different CRSs) or heterogenous CRSs across a single geometry attribute this may not be accurate.
   *
   * @param application the referenced application
   * @param simpleFeatureSource the feature source used in the application
   * @return {@code null} when no transform is required and a valid transform otherwise
   * @throws FactoryException when the CRS cannot be decoded
   */
  @Nullable public static MathTransform getTransformationToApplication(
      @NonNull Application application, @NonNull SimpleFeatureSource simpleFeatureSource)
      throws FactoryException {
    // this is the CRS of the "default geometry" attribute
    final CoordinateReferenceSystem dataSourceCRS =
        simpleFeatureSource.getSchema().getCoordinateReferenceSystem();
    final CoordinateReferenceSystem appCRS = CRS.decode(application.getCrs());
    if (!CRS.isEquivalent(dataSourceCRS, appCRS)) {
      return CRS.findMathTransform(dataSourceCRS, appCRS);
    } else {
      return null;
    }
  }

  /**
   * Determine whether we need to transform geometry to data source crs. Note that this uses the "default geometry"
   * attribute of the feature source, in cases where a feature source has multiple geometry attributes (with possibly
   * different CRSs) or heterogeneous CRSs across a single geometry attribute this may not be accurate.
   *
   * @param application the referenced application
   * @param simpleFeatureSource the feature source used in the application
   * @return {@code null} when no transform is required and a valid transform otherwise
   * @throws FactoryException when the CRS cannot be decoded
   */
  @Nullable public static MathTransform getTransformationToDataSource(
      @NonNull Application application, @NonNull SimpleFeatureSource simpleFeatureSource)
      throws FactoryException {
    MathTransform transform = null;
    // this is the CRS of the "default geometry" attribute
    final CoordinateReferenceSystem dataSourceCRS =
        simpleFeatureSource.getSchema().getCoordinateReferenceSystem();
    final CoordinateReferenceSystem appCRS = CRS.decode(application.getCrs());
    if (!CRS.isEquivalent(dataSourceCRS, appCRS)) {
      transform = CRS.findMathTransform(appCRS, dataSourceCRS);
    }
    return transform;
  }

  /**
   * Transforms the geometries in the given (intersects) spatial filter using the specified transformation.
   *
   * @param filter the spatial filter, currently only intersects filters are supported
   * @param transform the transformation to apply
   * @return the transformed filter
   */
  public static Filter transformFilterGeometries(@NonNull Filter filter, @Nullable MathTransform transform) {
    if (transform != null) {
      // Add transform for Intersects filter only, others not supported
      FilterVisitor visitor = new DefaultFilterVisitor() {
        @Override
        public Object visit(Intersects filter, Object data) {
          MathTransform transform = (MathTransform) data;
          logger.trace("Transforming filter geometry with: {}", transform);
          String geomWKT = filter.getExpression2().toString();
          logger.trace("Input filter geometry: {}", geomWKT);
          try {
            Geometry geom = wktReader.read(geomWKT);
            geom = JTS.transform(geom, transform);
            logger.trace("Transformed filter geometry: {}", geom.toText());
            if (filter instanceof IntersectsImpl intersectsImpl) {
              intersectsImpl.setExpression2(new LiteralExpressionImpl(geom));
            } else {
              logger.warn(
                  "Cannot update Intersects filter of type {}, leaving geometry untransformed. Filtered results will be inaccurate.",
                  filter.getClass().getName());
            }
          } catch (ParseException | TransformException e) {
            logger.error(
                "Error transforming filter geometry: {}. Filtered results will be inaccurate.",
                geomWKT,
                e);
          }
          return data;
        }
      };
      filter.accept(visitor, transform);
    }
    return filter;
  }
}
