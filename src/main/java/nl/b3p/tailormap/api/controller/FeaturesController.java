/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import static nl.b3p.tailormap.api.persistence.helper.TMAttributeTypeHelper.isGeometry;
import static nl.b3p.tailormap.api.persistence.helper.TMFeatureTypeHelper.getConfiguredAttributes;
import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

import io.micrometer.core.annotation.Timed;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import nl.b3p.tailormap.api.annotation.AppRestController;
import nl.b3p.tailormap.api.geotools.TransformationUtil;
import nl.b3p.tailormap.api.geotools.featuresources.FeatureSourceFactoryHelper;
import nl.b3p.tailormap.api.geotools.processing.GeometryProcessor;
import nl.b3p.tailormap.api.persistence.Application;
import nl.b3p.tailormap.api.persistence.GeoService;
import nl.b3p.tailormap.api.persistence.TMFeatureType;
import nl.b3p.tailormap.api.persistence.json.AppLayerSettings;
import nl.b3p.tailormap.api.persistence.json.AppTreeLayerNode;
import nl.b3p.tailormap.api.persistence.json.AttributeSettings;
import nl.b3p.tailormap.api.persistence.json.GeoServiceLayer;
import nl.b3p.tailormap.api.persistence.json.TMAttributeDescriptor;
import nl.b3p.tailormap.api.persistence.json.TMAttributeType;
import nl.b3p.tailormap.api.repository.FeatureSourceRepository;
import nl.b3p.tailormap.api.util.Constants;
import nl.b3p.tailormap.api.viewer.model.ColumnMetadata;
import nl.b3p.tailormap.api.viewer.model.Feature;
import nl.b3p.tailormap.api.viewer.model.FeaturesResponse;
import org.apache.commons.lang3.tuple.Pair;
import org.geotools.api.data.Query;
import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.filter.Filter;
import org.geotools.api.filter.FilterFactory;
import org.geotools.api.filter.sort.SortOrder;
import org.geotools.api.referencing.FactoryException;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.api.referencing.operation.TransformException;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.filter.text.cql2.CQLException;
import org.geotools.filter.text.ecql.ECQL;
import org.geotools.geometry.jts.JTS;
import org.geotools.util.factory.GeoTools;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.util.GeometricShapeFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@AppRestController
@Validated
@RequestMapping(
    path = "${tailormap-api.base-path}/{viewerKind}/{viewerName}/layer/{appLayerId}/features",
    produces = MediaType.APPLICATION_JSON_VALUE)
public class FeaturesController implements Constants {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final FeatureSourceFactoryHelper featureSourceFactoryHelper;

  private final FeatureSourceRepository featureSourceRepository;
  private final FilterFactory ff = CommonFactoryFinder.getFilterFactory(GeoTools.getDefaultHints());

  @Value("${tailormap-api.pageSize:100}")
  private int pageSize;

  @Value("${tailormap-api.features.wfs_count_exact:false}")
  private boolean exactWfsCounts;

  public FeaturesController(
      FeatureSourceFactoryHelper featureSourceFactoryHelper,
      FeatureSourceRepository featureSourceRepository) {
    this.featureSourceFactoryHelper = featureSourceFactoryHelper;
    this.featureSourceRepository = featureSourceRepository;
  }

  @Transactional
  @RequestMapping(method = {GET, POST})
  @Timed(value = "get_features", description = "time spent to process get features call")
  public ResponseEntity<Serializable> getFeatures(
      @ModelAttribute AppTreeLayerNode appTreeLayerNode,
      @ModelAttribute GeoService service,
      @ModelAttribute GeoServiceLayer layer,
      @ModelAttribute Application application,
      @RequestParam(required = false) Double x,
      @RequestParam(required = false) Double y,
      @RequestParam(defaultValue = "4") Double distance,
      @RequestParam(required = false) String __fid,
      @RequestParam(defaultValue = "false") Boolean simplify,
      @RequestParam(required = false) String filter,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) String sortBy,
      @RequestParam(required = false, defaultValue = "asc") String sortOrder,
      @RequestParam(defaultValue = "false") boolean onlyGeometries,
      @RequestParam(defaultValue = "false") boolean geometryInAttributes) {

    if (layer == null) {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND, "Can't find layer " + appTreeLayerNode);
    }

    TMFeatureType tmft = service.findFeatureTypeForLayer(layer, featureSourceRepository);
    if (tmft == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Layer does not have feature type");
    }
    AppLayerSettings appLayerSettings = application.getAppLayerSettings(appTreeLayerNode);

    if (onlyGeometries) {
      geometryInAttributes = true;
    }

    FeaturesResponse featuresResponse;

    if (null != __fid) {
      featuresResponse =
          getFeatureByFID(tmft, appLayerSettings, __fid, application, !geometryInAttributes);
    } else if (null != x && null != y) {
      featuresResponse =
          getFeaturesByXY(
              tmft, appLayerSettings, x, y, application, distance, simplify, !geometryInAttributes);
    } else if (null != page && page > 0) {
      featuresResponse =
          getAllFeatures(
              tmft,
              application,
              appLayerSettings,
              page,
              filter,
              sortBy,
              sortOrder,
              onlyGeometries,
              !geometryInAttributes);
    } else {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Unsupported combination of request parameters");
    }

    return ResponseEntity.status(HttpStatus.OK).body(featuresResponse);
  }

  @NotNull
  private FeaturesResponse getAllFeatures(
      @NotNull TMFeatureType tmft,
      @NotNull Application application,
      @NotNull AppLayerSettings appLayerSettings,
      Integer page,
      String filterCQL,
      String sortBy,
      String sortOrder,
      boolean onlyGeometries,
      boolean skipGeometryOutput) {
    FeaturesResponse featuresResponse = new FeaturesResponse().page(page).pageSize(pageSize);

    SimpleFeatureSource fs = null;
    try {
      fs = featureSourceFactoryHelper.openGeoToolsFeatureSource(tmft);

      // TODO evaluate; do we want geometry in this response or not?
      //  if we do the geometry attribute must not be removed from propNames

      // Property names for query: only non-geometry attributes that aren't hidden
      List<String> propNames =
          getConfiguredAttributes(tmft, appLayerSettings).values().stream()
              .map(Pair::getLeft)
              .filter(a -> !isGeometry(a.getType()))
              .map(TMAttributeDescriptor::getName)
              .collect(Collectors.toList());

      String sortAttrName;
      if (onlyGeometries) {
        propNames = List.of(tmft.getDefaultGeometryAttribute());
        // do not try to sort by geometry
        sortAttrName = null;
      } else {
        if (propNames.isEmpty()) {
          return featuresResponse;
        }
        // Default sorting attribute if sortBy not specified or not a configured attribute
        if (tmft.getPrimaryKeyAttribute() != null
            && propNames.contains(tmft.getPrimaryKeyAttribute())) {
          // There is a primary key and it is known, use that for sorting
          sortAttrName = tmft.getPrimaryKeyAttribute();
        } else {
          sortAttrName = propNames.get(0);
        }

        if (null != sortBy) {
          // Only use sortBy attribute if it is in the list of configured attributes and not a
          // geometry type (propNames does not contain geometry attributes, see above)
          if (propNames.contains(sortBy)) {
            sortAttrName = sortBy;
          } else {
            logger.warn(
                "Requested sortBy attribute {} was not found in configured attributes or is a geometry attribute",
                sortBy);
          }
        }
      }

      SortOrder _sortOrder = SortOrder.ASCENDING;
      if (null != sortOrder
          && (sortOrder.equalsIgnoreCase("desc") || sortOrder.equalsIgnoreCase("asc"))) {
        _sortOrder = SortOrder.valueOf(sortOrder.toUpperCase(Locale.ROOT));
      }

      // setup query, attributes and filter
      Query q = new Query(fs.getName().toString());
      q.setPropertyNames(propNames);

      // count can be -1 if too costly eg. some WFS
      int featureCount;
      if (null != filterCQL) {
        Filter filter = ECQL.toFilter(filterCQL);
        q.setFilter(filter);
        featureCount = fs.getCount(q);
        // this will execute the query twice, once to get the count and once to get the data
        if (featureCount == -1 && exactWfsCounts) {
          featureCount = fs.getFeatures(q).size();
        }
      } else {
        featureCount = fs.getCount(Query.ALL);
        // this will execute the query twice, once to get the count and once to get the data
        if (featureCount == -1 && exactWfsCounts) {
          featureCount = fs.getFeatures(Query.ALL).size();
        }
      }
      featuresResponse.setTotal(featureCount);

      // setup page query
      if (sortAttrName != null) {
        q.setSortBy(ff.sort(sortAttrName, _sortOrder));
      }
      q.setMaxFeatures(pageSize);
      q.setStartIndex((page - 1) * pageSize);
      logger.debug("Attribute query: {}", q);

      executeQueryOnFeatureSourceAndClose(
          false,
          featuresResponse,
          tmft,
          appLayerSettings,
          onlyGeometries,
          fs,
          q,
          application,
          skipGeometryOutput);
    } catch (IOException e) {
      logger.error("Could not retrieve attribute data.", e);
    } catch (CQLException e) {
      logger.error("Could not parse requested filter.", e);
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not parse requested filter");
    } finally {
      if (fs != null) {
        fs.getDataStore().dispose();
      }
    }

    return featuresResponse;
  }

  @NotNull
  private FeaturesResponse getFeatureByFID(
      @NotNull TMFeatureType tmFeatureType,
      @NotNull AppLayerSettings appLayerSettings,
      @NotNull String fid,
      @NotNull Application application,
      boolean skipGeometryOutput) {
    FeaturesResponse featuresResponse = new FeaturesResponse();

    SimpleFeatureSource fs = null;
    try {
      fs = featureSourceFactoryHelper.openGeoToolsFeatureSource(tmFeatureType);
      Query q = new Query(fs.getName().toString());
      q.setFilter(ff.id(ff.featureId(fid)));
      q.setMaxFeatures(1);
      logger.debug("FID query: {}", q);

      executeQueryOnFeatureSourceAndClose(
          false,
          featuresResponse,
          tmFeatureType,
          appLayerSettings,
          false,
          fs,
          q,
          application,
          skipGeometryOutput);
    } catch (IOException e) {
      logger.error("Could not retrieve attribute data", e);
    } finally {
      if (fs != null) {
        fs.getDataStore().dispose();
      }
    }

    return featuresResponse;
  }

  @NotNull
  private FeaturesResponse getFeaturesByXY(
      @NotNull TMFeatureType tmFeatureType,
      @NotNull AppLayerSettings appLayerSettings,
      @NotNull Double x,
      @NotNull Double y,
      @NotNull Application application,
      @NotNull Double distance,
      @NotNull Boolean simplifyGeometry,
      boolean skipGeometryOutput) {

    if (null != distance && 0d >= distance) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Buffer distance must be greater than 0");
    }

    FeaturesResponse featuresResponse = new FeaturesResponse();

    SimpleFeatureSource fs;
    try {
      GeometricShapeFactory shapeFact = new GeometricShapeFactory();
      shapeFact.setNumPoints(32);
      shapeFact.setCentre(new Coordinate(x, y));
      //noinspection ConstantConditions
      shapeFact.setSize(distance * 2d);
      Geometry p = shapeFact.createCircle();
      logger.debug("created geometry: {}", p);

      MathTransform transform = null;
      fs = featureSourceFactoryHelper.openGeoToolsFeatureSource(tmFeatureType);
      try {
        transform = TransformationUtil.getTransformationToDataSource(application, fs);
      } catch (FactoryException e) {
        logger.warn("Unable to find transformation from query geometry to desired datasource", e);
      }
      if (null != transform) {
        try {
          p = JTS.transform(p, transform);
          logger.debug("reprojected geometry to: {}", p);
        } catch (TransformException e) {
          logger.warn(
              "Unable to transform query geometry to desired CRS, trying with original CRS");
        }
      }
      logger.debug("using selection geometry: {}", p);
      Filter spatialFilter =
          ff.intersects(ff.property(tmFeatureType.getDefaultGeometryAttribute()), ff.literal(p));

      // TODO flamingo does some fancy stuff to combine with existing filters using
      //      TailormapCQL and some filter visitors
      Query q = new Query(fs.getName().toString());
      q.setFilter(spatialFilter);
      q.setMaxFeatures(DEFAULT_MAX_FEATURES);

      executeQueryOnFeatureSourceAndClose(
          simplifyGeometry,
          featuresResponse,
          tmFeatureType,
          appLayerSettings,
          false,
          fs,
          q,
          application,
          skipGeometryOutput);
    } catch (IOException e) {
      logger.error("Could not retrieve attribute data", e);
    }
    return featuresResponse;
  }

  private void executeQueryOnFeatureSourceAndClose(
      boolean simplifyGeometry,
      @NotNull FeaturesResponse featuresResponse,
      @NotNull TMFeatureType tmFeatureType,
      @NotNull AppLayerSettings appLayerSettings,
      boolean onlyGeometries,
      @NotNull SimpleFeatureSource featureSource,
      @NotNull Query selectQuery,
      @NotNull Application application,
      boolean skipGeometryOutput)
      throws IOException {
    boolean addFields = false;

    MathTransform transform = null;
    try {
      transform = TransformationUtil.getTransformationToApplication(application, featureSource);
    } catch (FactoryException e) {
      logger.error("Can not transform geometry to desired CRS", e);
    }

    Map<String, Pair<TMAttributeDescriptor, AttributeSettings>> configuredAttributes =
        getConfiguredAttributes(tmFeatureType, appLayerSettings);

    // send request to attribute source
    try (SimpleFeatureIterator feats = featureSource.getFeatures(selectQuery).features()) {
      while (feats.hasNext()) {
        addFields = true;
        // transform found simplefeatures to list of Feature
        SimpleFeature feature = feats.next();
        // processedGeometry can be null
        String processedGeometry =
            GeometryProcessor.processGeometry(
                feature.getAttribute(tmFeatureType.getDefaultGeometryAttribute()),
                simplifyGeometry,
                true,
                transform);
        Feature newFeat =
            new Feature().fid(feature.getIdentifier().getID()).geometry(processedGeometry);

        if (!onlyGeometries) {
          for (String attName : configuredAttributes.keySet()) {
            Object value = feature.getAttribute(attName);
            if (value instanceof Geometry) {
              if (skipGeometryOutput) {
                value = null;
              } else {
                value = GeometryProcessor.geometryToWKT((Geometry) value);
              }
            }
            newFeat.putAttributesItem(attName, value);
          }
        }
        featuresResponse.addFeaturesItem(newFeat);
      }
    } finally {
      featureSource.getDataStore().dispose();
    }
    if (addFields) {
      configuredAttributes.values().stream()
          .map(
              pair -> {
                TMAttributeDescriptor attributeDescriptor = pair.getLeft();
                TMAttributeType type = attributeDescriptor.getType();
                AttributeSettings settings = pair.getRight();
                return new ColumnMetadata()
                    .key(attributeDescriptor.getName())
                    .alias(settings.getTitle())
                    .type(isGeometry(type) ? TMAttributeType.GEOMETRY : type);
              })
          .forEach(featuresResponse::addColumnMetadataItem);
    }
  }
}
