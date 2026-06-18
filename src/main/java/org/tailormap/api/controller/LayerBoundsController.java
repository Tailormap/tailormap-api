/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

import io.micrometer.core.annotation.Timed;
import java.io.IOException;
import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.util.regex.Pattern;
import org.geotools.api.data.Query;
import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.api.filter.Filter;
import org.geotools.api.referencing.FactoryException;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.data.oracle.OracleDialect;
import org.geotools.data.postgis.PostGISDialect;
import org.geotools.data.sqlserver.SQLServerDialect;
import org.geotools.filter.text.cql2.CQLException;
import org.geotools.filter.text.ecql.ECQL;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.jdbc.JDBCDataStore;
import org.locationtech.jts.geom.Envelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.tailormap.api.annotation.AppRestController;
import org.tailormap.api.geotools.TransformationUtil;
import org.tailormap.api.geotools.featuresources.FeatureSourceFactoryHelper;
import org.tailormap.api.geotools.processing.GeometryProcessor;
import org.tailormap.api.persistence.Application;
import org.tailormap.api.persistence.GeoService;
import org.tailormap.api.persistence.TMFeatureType;
import org.tailormap.api.persistence.helper.GeoToolsHelper;
import org.tailormap.api.persistence.json.AppTreeLayerNode;
import org.tailormap.api.persistence.json.GeoServiceLayer;
import org.tailormap.api.repository.FeatureSourceRepository;

@AppRestController
@Validated
@RequestMapping(
    path = "${tailormap-api.base-path}/{viewerKind}/{viewerName}/layer/{appLayerId}/bounds",
    produces = MediaType.APPLICATION_JSON_VALUE)
public class LayerBoundsController {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final FeatureSourceFactoryHelper featureSourceFactoryHelper;

  private final FeatureSourceRepository featureSourceRepository;

  public LayerBoundsController(
      FeatureSourceFactoryHelper featureSourceFactoryHelper, FeatureSourceRepository featureSourceRepository) {
    this.featureSourceFactoryHelper = featureSourceFactoryHelper;
    this.featureSourceRepository = featureSourceRepository;
  }

  @Transactional
  @RequestMapping(method = {GET, POST})
  @Timed(value = "calculate_layer_bounds", description = "time spent calculating (filtered) layer bounds")
  public ResponseEntity<Serializable> calculateLayerBounds(
      @ModelAttribute AppTreeLayerNode appTreeLayerNode,
      @ModelAttribute GeoService service,
      @ModelAttribute GeoServiceLayer layer,
      @ModelAttribute Application application,
      @RequestParam(required = false, name = "filter") String filterCQL) {

    if (layer == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Can't find layer " + appTreeLayerNode);
    }

    TMFeatureType tmft = service.findFeatureTypeForLayer(layer, featureSourceRepository);
    if (tmft == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Layer does not have feature type");
    }

    SimpleFeatureSource featureSource = null;
    try {
      featureSource = featureSourceFactoryHelper.openGeoToolsFeatureSource(tmft);
      Query query = new Query(tmft.getName());
      query.setHandle("calculateLayerBounds");

      if (filterCQL != null && !filterCQL.isEmpty()) {
        try {
          Filter parsedFilter = ECQL.toFilter(filterCQL);

          // check if the application is in the same CRS as the feature type and we have a query that applies
          // to the default geometry
          MathTransform transform =
              TransformationUtil.getTransformationToDataSource(application, featureSource);
          if (transform != null
              // the filter CQL will contain something like "INTERSECTS(geom,..." so look for the
              // opening '(' and closing ',' allowing whitespace so it is not too brittle wrt. formatting
              // note that more than 1 spatial filters may be present, just look for any atm
              && Pattern.compile("\\(\\s*" + Pattern.quote(tmft.getDefaultGeometryAttribute()) + "\\s*,")
                  .matcher(filterCQL)
                  .find()) {
            // TODO https://b3partners.atlassian.net/browse/HTM-2088
            //      we need to transform the geometry/geometries in the filter to the feature source CRS
            //       before applying the filter, for now log a warning and continue
            logger.warn(
                "Application CRS is different from feature source CRS and filter '{}' contains spatial predicates on the default geometry, but filter geometries are not transformed. This will lead to incorrect results or errors.",
                filterCQL);
          }

          query.setFilter(parsedFilter);
        } catch (CQLException e) {
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
      }

      if (filterCQL == null && featureSource.getDataStore() instanceof JDBCDataStore jdbcDataStore) {
        // turn off getting optimised/inaccurate bounds
        logger.debug(
            "Turning off estimated extents for layer {} because no filter is applied and the datastore is a JDBCDataStore",
            appTreeLayerNode.getLayerName());

        switch (jdbcDataStore.getSQLDialect()) {
          case SQLServerDialect sqlServerDialect -> sqlServerDialect.setEstimatedExtentsEnabled(false);
          case PostGISDialect postGISDialect -> postGISDialect.setEstimatedExtentsEnabled(false);
          case OracleDialect oracleDialect -> oracleDialect.setEstimatedExtentsEnabled(false);
          default -> {
            // no-op
          }
        }
      }

      // some datastores optimize getting bounds by using metadata or spatial index; this is inaccurate.
      // Also featureSource.getBounds(query) can return null in case the GT API thinks it is too costly...
      ReferencedEnvelope referencedEnvelope = featureSource.getBounds(query);
      if (referencedEnvelope == null) {
        referencedEnvelope = featureSource.getFeatures(query).getBounds();
      }

      if (referencedEnvelope.boundsEquals2D(new ReferencedEnvelope(), 0.1)) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "No features found for layer " + appTreeLayerNode.getLayerName() + " and filter '" + filterCQL
                + "'");
      }

      // if the featuretype CRS is different from the application CRS we need to project to application
      Envelope envelope = GeometryProcessor.transformEnvelope(
          referencedEnvelope, TransformationUtil.getTransformationToApplication(application, featureSource));

      return ResponseEntity.ok(GeoToolsHelper.fromEnvelope(envelope));
    } catch (FactoryException | IOException e) {
      logger.error("Error calculating bounds for layer {} and filter '{}'", appTreeLayerNode, filterCQL, e);
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "Error calculating layer bounds for: " + appTreeLayerNode.getLayerName() + ". " + e.getMessage(),
          e);
    } finally {
      if (featureSource != null) {
        featureSource.getDataStore().dispose();
      }
    }
  }
}
