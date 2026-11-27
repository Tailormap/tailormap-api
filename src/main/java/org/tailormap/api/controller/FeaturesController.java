/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;
import static org.tailormap.api.persistence.helper.TMAttributeTypeHelper.isGeometry;
import static org.tailormap.api.persistence.helper.TMFeatureTypeHelper.getConfiguredAttributes;

import io.micrometer.core.annotation.Timed;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
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
import org.geotools.data.wfs.WFSDataStore;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.filter.text.cql2.CQLException;
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
import org.tailormap.api.annotation.AppRestController;
import org.tailormap.api.geotools.FilterUtil;
import org.tailormap.api.geotools.TransformationUtil;
import org.tailormap.api.geotools.featuresources.AttachmentsHelper;
import org.tailormap.api.geotools.featuresources.FeatureSourceFactoryHelper;
import org.tailormap.api.geotools.featuresources.WFSFeatureSourceHelper;
import org.tailormap.api.geotools.processing.GeometryProcessor;
import org.tailormap.api.geotools.wfs.WFSCountDefaultUtil;
import org.tailormap.api.geotools.wfs.WFSHitsCountUtil;
import org.tailormap.api.persistence.Application;
import org.tailormap.api.persistence.GeoService;
import org.tailormap.api.persistence.TMFeatureSource;
import org.tailormap.api.persistence.TMFeatureType;
import org.tailormap.api.persistence.helper.TMFeatureTypeHelper;
import org.tailormap.api.persistence.json.AppLayerSettings;
import org.tailormap.api.persistence.json.AppTreeLayerNode;
import org.tailormap.api.persistence.json.FeatureTypeTemplate;
import org.tailormap.api.persistence.json.GeoServiceLayer;
import org.tailormap.api.persistence.json.TMAttributeDescriptor;
import org.tailormap.api.persistence.json.TMAttributeType;
import org.tailormap.api.repository.FeatureSourceRepository;
import org.tailormap.api.util.Constants;
import org.tailormap.api.viewer.model.AttachmentMetadata;
import org.tailormap.api.viewer.model.ColumnMetadata;
import org.tailormap.api.viewer.model.Feature;
import org.tailormap.api.viewer.model.FeaturesResponse;

@AppRestController
@Validated
@RequestMapping(
    path = "${tailormap-api.base-path}/{viewerKind}/{viewerName}/layer/{appLayerId}/features",
    produces = MediaType.APPLICATION_JSON_VALUE)
public class FeaturesController implements Constants {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final FeatureSourceFactoryHelper featureSourceFactoryHelper;
  private final TMFeatureTypeHelper featureTypeHelper;
  private final FeatureSourceRepository featureSourceRepository;
  private final FilterFactory ff = CommonFactoryFinder.getFilterFactory(GeoTools.getDefaultHints());

  @Value("${tailormap-api.default-page-size:100}")
  private int defaultPageSize;

  @Value("${tailormap-api.max-page-size:500}")
  private int maxPageSize;

  @Value("${tailormap-api.feature.info.maxitems:30}")
  private int maxFeatures;

  public FeaturesController(
      FeatureSourceFactoryHelper featureSourceFactoryHelper,
      TMFeatureTypeHelper featureTypeHelper,
      FeatureSourceRepository featureSourceRepository) {
    this.featureSourceFactoryHelper = featureSourceFactoryHelper;
    this.featureTypeHelper = featureTypeHelper;
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
      @RequestParam(required = false) Integer pageSize,
      @RequestParam(required = false) String sortBy,
      @RequestParam(required = false, defaultValue = "asc") String sortOrder,
      @RequestParam(defaultValue = "false") boolean onlyGeometries,
      @RequestParam(defaultValue = "false") boolean geometryInAttributes,
      @RequestParam(defaultValue = "false") boolean withAttachments) {

    if (layer == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Can't find layer " + appTreeLayerNode);
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
          getFeatureByFID(tmft, appLayerSettings, __fid, application, !geometryInAttributes, withAttachments);
    } else if (null != x && null != y) {
      featuresResponse = getFeaturesByXY(
          tmft,
          appLayerSettings,
          filter,
          x,
          y,
          application,
          distance,
          simplify,
          !geometryInAttributes,
          withAttachments);
    } else if (null != page && page > 0) {
      featuresResponse = getAllFeatures(
          tmft,
          application,
          appLayerSettings,
          page,
          pageSize,
          filter,
          sortBy,
          sortOrder,
          simplify,
          onlyGeometries,
          !geometryInAttributes,
          withAttachments);
    } else {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported combination of request parameters");
    }

    return ResponseEntity.status(HttpStatus.OK).body(featuresResponse);
  }

  @NotNull private FeaturesResponse getAllFeatures(
      @NotNull TMFeatureType tmft,
      @NotNull Application application,
      @NotNull AppLayerSettings appLayerSettings,
      Integer page,
      Integer pageSize,
      String filterECQL,
      String sortBy,
      String sortOrder,
      boolean simplifyGeometry,
      boolean onlyGeometries,
      boolean skipGeometryOutput,
      boolean withAttachments) {
    int requestedPageSize = pageSize != null ? pageSize : defaultPageSize;
    requestedPageSize = Math.max(1, requestedPageSize);
    int requestPageSize = Math.min(maxPageSize, requestedPageSize);
    FeaturesResponse featuresResponse = new FeaturesResponse().page(page).pageSize(requestPageSize);

    SimpleFeatureSource fs = null;
    try {
      fs = featureSourceFactoryHelper.openGeoToolsFeatureSource(tmft);

      // A WFS 1.x has no offset: GeoTools only sends MAXFEATURES and skips the start index in memory, so a
      // next page would silently be empty. Use WFS 2.0.0 for paging.
      if (page > 1
          && fs.getDataStore() instanceof WFSDataStore wfsDataStore
          && !wfsDataStore.getWfsClient().canOffset()) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Paging is not supported for this WFS version, only the first page can be retrieved. Use WFS 2.0.0");
      }

      // A WFS returns at most its CountDefault features. Use that as the page size (returned in the response so
      // the client uses it too), otherwise the start index of the next page would skip features, and a client
      // that does not know the total would think a page with fewer features than the page size is the last page.
      Integer wfsCountDefault = WFSCountDefaultUtil.getCountDefault(fs.getDataStore());
      if (wfsCountDefault != null && wfsCountDefault < requestPageSize) {
        requestPageSize = wfsCountDefault;
        featuresResponse.setPageSize(requestPageSize);
      }

      // Property names for sorting: only non-geometry attributes that aren't hidden
      List<String> propNames = getConfiguredAttributes(tmft, appLayerSettings).values().stream()
          .map(TMFeatureTypeHelper.AttributeWithSettings::attributeDescriptor)
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
        if (tmft.getPrimaryKeyAttribute() != null && propNames.contains(tmft.getPrimaryKeyAttribute())) {
          // There is a primary key and it is known, use that for sorting
          sortAttrName = tmft.getPrimaryKeyAttribute();
        } else if (tmft.getFeatureSource().getProtocol() == TMFeatureSource.Protocol.WFS
            && "2.0.0"
                .equals(WFSFeatureSourceHelper.getWFSVersion(
                    tmft.getFeatureSource().getUrl()))) {
          // A WFS has no known primary key (only detected for JDBC) and the first attribute need not be
          // unique, so use the natural order of the server. WFS 2.0 (ISO 19142) 7.9.2.5.4.4 requires a server
          // to keep the order of a query without a sorting clause the same across executions, so it is stable
          // across pages (MapServer with PostGIS doesn't strictly do this: no ORDER BY with LIMIT/OFFSET).
          // Sorting on gml:id is not possible (PDOK and GeoServer reject it).
          // Not for WFS 1.x: GeoTools then sends the NATURAL_ORDER it adds when paging as a SortProperty
          // without a PropertyName, which GeoServer rejects.
          sortAttrName = null;
        } else {
          // A database has no guaranteed order without ORDER BY. For WFS 1.x, sort on the first attribute
          sortAttrName = propNames.getFirst();
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
      if (null != sortOrder && (sortOrder.equalsIgnoreCase("desc") || sortOrder.equalsIgnoreCase("asc"))) {
        _sortOrder = SortOrder.valueOf(sortOrder.toUpperCase(Locale.ROOT));
      }

      // setup query, attributes and filter
      Query q = new Query(fs.getName().toString());

      // add default geometry attribute to the property names for query
      if (!skipGeometryOutput && !onlyGeometries) {
        propNames.add(tmft.getDefaultGeometryAttribute());
      }
      q.setPropertyNames(propNames);

      // count can be -1 if too costly or unknown, eg. some WFS
      int featureCount;
      if (null != filterECQL) {
        Filter filter = parseFilter(filterECQL, application, fs);
        q.setFilter(filter);
        featureCount = WFSHitsCountUtil.getCount(fs, q);
      } else {
        featureCount = WFSHitsCountUtil.getCount(fs, Query.ALL);
      }
      featuresResponse.setTotal(featureCount);

      // setup page query
      if (sortAttrName != null) {
        q.setSortBy(ff.sort(sortAttrName, _sortOrder));
      }
      q.setMaxFeatures(requestPageSize);
      // Only set a start index for pages after the first. With a start index and no sort order, GeoTools adds
      // SortBy.NATURAL_ORDER, which a WFS 1.1.0 client sends to the server as a SortProperty without a
      // PropertyName that GeoServer and MapServer reject. Without a sort order this happens for onlyGeometries.
      if (page > 1) {
        q.setStartIndex((page - 1) * requestPageSize);
      }
      logger.debug("Attribute query: {}", q);

      executeQueryOnFeatureSourceAndClose(
          simplifyGeometry,
          featuresResponse,
          tmft,
          appLayerSettings,
          onlyGeometries,
          fs,
          q,
          application,
          skipGeometryOutput,
          withAttachments);
    } catch (ResponseStatusException e) {
      throw e;
    } catch (IOException | RuntimeException e) {
      throw featuresReadError(tmft, e);
    } finally {
      if (fs != null) {
        fs.getDataStore().dispose();
      }
    }

    return featuresResponse;
  }

  /**
   * Parse an ECQL filter from the request.
   *
   * @throws ResponseStatusException with status 400 if the filter is invalid
   */
  private static Filter parseFilter(String filterECQL, Application application, SimpleFeatureSource fs) {
    try {
      return FilterUtil.parseFilter(filterECQL, application, fs);
    } catch (CQLException | FactoryException | UnsupportedOperationException e) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Could not parse requested filter: " + e.getMessage(), e);
    }
  }

  /**
   * Log an error that occurred while reading features and create an exception to report it to the client, instead of
   * returning an empty response or a server error without a message.
   */
  private static ResponseStatusException featuresReadError(TMFeatureType tmFeatureType, Exception e) {
    logger.error("Error reading features of feature type {}", tmFeatureType.getName(), e);
    return new ResponseStatusException(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Error reading features of feature type \"%s\": %s"
            .formatted(
                tmFeatureType.getName(),
                e.getMessage() != null
                    ? e.getMessage()
                    : e.getClass().getName()),
        e);
  }

  @NotNull private FeaturesResponse getFeatureByFID(
      @NotNull TMFeatureType tmFeatureType,
      @NotNull AppLayerSettings appLayerSettings,
      @NotNull String fid,
      @NotNull Application application,
      boolean skipGeometryOutput,
      boolean withAttachments) {
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
          skipGeometryOutput,
          withAttachments);
    } catch (ResponseStatusException e) {
      throw e;
    } catch (IOException | RuntimeException e) {
      throw featuresReadError(tmFeatureType, e);
    } finally {
      if (fs != null) {
        fs.getDataStore().dispose();
      }
    }

    return featuresResponse;
  }

  @NotNull private FeaturesResponse getFeaturesByXY(
      @NotNull TMFeatureType tmFeatureType,
      @NotNull AppLayerSettings appLayerSettings,
      String filterECQL,
      @NotNull Double x,
      @NotNull Double y,
      @NotNull Application application,
      @NotNull Double distance,
      @NotNull Boolean simplifyGeometry,
      boolean skipGeometryOutput,
      boolean withAttachments) {

    if (null != distance && 0d >= distance) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Buffer distance must be greater than 0");
    }

    FeaturesResponse featuresResponse = new FeaturesResponse();

    SimpleFeatureSource fs = null;
    try {
      GeometricShapeFactory shapeFact = new GeometricShapeFactory();
      shapeFact.setNumPoints(32);
      shapeFact.setCentre(new Coordinate(x, y));
      //noinspection ConstantConditions
      shapeFact.setSize(distance * 2d);
      Geometry p = shapeFact.createCircle();
      logger.trace("created selection geometry: {}", p);

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
          logger.trace("reprojected selection geometry to: {}", p);
        } catch (TransformException e) {
          logger.warn("Unable to transform query geometry to desired CRS, trying with original CRS");
        }
      }
      logger.trace("using selection geometry: {}", p);
      Filter spatialFilter =
          ff.intersects(ff.property(tmFeatureType.getDefaultGeometryAttribute()), ff.literal(p));

      Filter finalFilter = spatialFilter;
      if (null != filterECQL) {
        Filter filter = parseFilter(filterECQL, application, fs);
        finalFilter = ff.and(spatialFilter, filter);
      }
      Query q = new Query(fs.getName().toString());
      q.setFilter(finalFilter);
      q.setMaxFeatures(maxFeatures);

      executeQueryOnFeatureSourceAndClose(
          simplifyGeometry,
          featuresResponse,
          tmFeatureType,
          appLayerSettings,
          false,
          fs,
          q,
          application,
          skipGeometryOutput,
          withAttachments);
    } catch (ResponseStatusException e) {
      throw e;
    } catch (IOException | RuntimeException e) {
      throw featuresReadError(tmFeatureType, e);
    } finally {
      if (fs != null) {
        // Normally executeQueryOnFeatureSourceAndClose() will close the datastore, but when filterECQL is
        // invalid the datastore will be left open, so close here to make sure (disposing twice won't hurt)
        fs.getDataStore().dispose();
      }
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
      boolean skipGeometryOutput,
      boolean withAttachments)
      throws IOException {
    boolean addFields = false;

    MathTransform transform = null;
    try {
      transform = TransformationUtil.getTransformationToApplication(application, featureSource);
    } catch (FactoryException e) {
      logger.error("Can not transform geometry to desired CRS", e);
    }

    boolean ftSupportsAttachments = tmFeatureType.getSettings().getAttachmentAttributes() != null
        && !tmFeatureType.getSettings().getAttachmentAttributes().isEmpty();

    List<Object> featurePKs = new ArrayList<>();
    Map<String, TMFeatureTypeHelper.AttributeWithSettings> configuredAttributes =
        getConfiguredAttributes(tmFeatureType, appLayerSettings);

    // send request to attribute source
    try (SimpleFeatureIterator feats =
        featureSource.getFeatures(selectQuery).features()) {
      while (feats.hasNext()) {
        addFields = true;
        // transform found simplefeatures to list of Feature
        SimpleFeature feature = feats.next();

        // processedGeometry can be null
        String processedGeometry = GeometryProcessor.processGeometry(
            feature.getAttribute(tmFeatureType.getDefaultGeometryAttribute()),
            simplifyGeometry,
            true,
            transform);
        Feature newFeat = new Feature().fid(feature.getID()).geometry(processedGeometry);

        if (!onlyGeometries) {
          for (String attName : configuredAttributes.keySet()) {
            Object value = feature.getAttribute(attName);
            if (value instanceof Geometry geometry) {
              if (skipGeometryOutput) {
                value = null;
              } else {
                value = GeometryProcessor.geometryToWKT(geometry);
              }
            }
            newFeat.putAttributesItem(attName, value);
          }
          if (withAttachments && ftSupportsAttachments) {
            // Just add the PK as is, no conversion needed
            featurePKs.add(feature.getAttribute(tmFeatureType.getPrimaryKeyAttribute()));
          }
        }
        featuresResponse.addFeaturesItem(newFeat);
      }
    } finally {
      featureSource.getDataStore().dispose();
    }
    FeatureTypeTemplate ftt = tmFeatureType.getSettings().getTemplate();
    if (ftt != null) {
      featuresResponse.setTemplate(ftt.getTemplate());
    }
    if (addFields) {
      configuredAttributes.values().stream()
          .map(attributeWithSettings -> {
            TMAttributeType type =
                attributeWithSettings.attributeDescriptor().getType();
            return new ColumnMetadata()
                .name(attributeWithSettings
                    .attributeDescriptor()
                    .getName())
                .alias(attributeWithSettings.settings().getTitle())
                .type(isGeometry(type) ? TMAttributeType.GEOMETRY : type);
          })
          .forEach(featuresResponse::addColumnMetadataItem);
    }
    if (ftSupportsAttachments) {
      //  add attachment metadata
      featuresResponse.setAttachmentMetadata(
          featureTypeHelper.getAttachmentAttributesWithMaxFileUploadSize(tmFeatureType));

      if (withAttachments) {
        //  fetch all attachments for all features, grouped by feature fid
        Map<String, List<AttachmentMetadata>> attachmentsByFeatureId =
            AttachmentsHelper.listAttachmentsForFeaturesByFeatureId(tmFeatureType, featurePKs);
        //  add attachment data to features using the feature FID to match
        for (Feature feature : featuresResponse.getFeatures()) {
          String primaryKey = feature.getFid();
          List<AttachmentMetadata> attachments = attachmentsByFeatureId.get(primaryKey);
          if (attachments != null) {
            feature.setAttachments(attachments);
          }
        }
      }
    }
  }
}
