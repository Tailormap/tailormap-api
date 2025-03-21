/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller;

import static org.tailormap.api.persistence.helper.TMFeatureTypeHelper.getNonHiddenAttributeNames;
import static org.tailormap.api.persistence.helper.TMFeatureTypeHelper.getNonHiddenAttributes;
import static org.tailormap.api.persistence.helper.TMFeatureTypeHelper.getReadOnlyAttributes;

import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import java.io.IOException;
import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.geotools.api.data.FeatureStore;
import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.api.data.SimpleFeatureStore;
import org.geotools.api.data.Transaction;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.type.AttributeDescriptor;
import org.geotools.api.filter.Filter;
import org.geotools.api.filter.FilterFactory;
import org.geotools.api.filter.identity.FeatureId;
import org.geotools.api.referencing.FactoryException;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.data.DataUtilities;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.util.factory.GeoTools;
import org.geotools.util.factory.Hints;
import org.locationtech.jts.geom.Geometry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import org.tailormap.api.annotation.AppRestController;
import org.tailormap.api.geotools.TransformationUtil;
import org.tailormap.api.geotools.featuresources.FeatureSourceFactoryHelper;
import org.tailormap.api.geotools.processing.GeometryProcessor;
import org.tailormap.api.persistence.Application;
import org.tailormap.api.persistence.GeoService;
import org.tailormap.api.persistence.TMFeatureType;
import org.tailormap.api.persistence.helper.TMAttributeTypeHelper;
import org.tailormap.api.persistence.json.AppLayerSettings;
import org.tailormap.api.persistence.json.AppTreeLayerNode;
import org.tailormap.api.persistence.json.GeoServiceLayer;
import org.tailormap.api.repository.FeatureSourceRepository;
import org.tailormap.api.util.Constants;
import org.tailormap.api.viewer.model.Feature;

@AppRestController
@Validated
@RequestMapping(path = {"${tailormap-api.base-path}/{viewerKind}/{viewerName}/layer/{appLayerId}/edit/feature"})
public class EditFeatureController implements Constants {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final FeatureSourceFactoryHelper featureSourceFactoryHelper;
  private final FilterFactory ff = CommonFactoryFinder.getFilterFactory(GeoTools.getDefaultHints());
  private final FeatureSourceRepository featureSourceRepository;

  public EditFeatureController(
      FeatureSourceFactoryHelper featureSourceFactoryHelper, FeatureSourceRepository featureSourceRepository) {
    this.featureSourceFactoryHelper = featureSourceFactoryHelper;
    this.featureSourceRepository = featureSourceRepository;
  }

  private static void checkFeatureHasOnlyValidAttributes(
      Feature feature, TMFeatureType tmFeatureType, AppLayerSettings appLayerSettings) {

    if (!getNonHiddenAttributeNames(tmFeatureType, appLayerSettings)
        .containsAll(feature.getAttributes().keySet())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Feature cannot be edited, one or more requested attributes are not available on the feature type");
    }
    if (!Collections.disjoint(
        getReadOnlyAttributes(tmFeatureType, appLayerSettings),
        feature.getAttributes().keySet())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Feature cannot be edited, one or more requested attributes are not editable on the feature type");
    }
  }

  @Transactional
  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  @Timed(value = "create_feature", description = "time spent to process create feature call")
  @Counted(value = "create_feature", description = "number of create feature calls")
  public ResponseEntity<Serializable> createFeature(
      @ModelAttribute AppTreeLayerNode appTreeLayerNode,
      @ModelAttribute GeoService service,
      @ModelAttribute GeoServiceLayer layer,
      @ModelAttribute Application application,
      @RequestBody Feature completeFeature) {

    checkAuthentication();

    TMFeatureType tmFeatureType = getEditableFeatureType(application, appTreeLayerNode, service, layer);
    Map<String, Object> attributesMap = completeFeature.getAttributes();

    AppLayerSettings appLayerSettings = application.getAppLayerSettings(appTreeLayerNode);

    checkFeatureHasOnlyValidAttributes(completeFeature, tmFeatureType, appLayerSettings);

    Feature newFeature;
    SimpleFeatureSource fs = null;

    try (Transaction transaction = new DefaultTransaction("create")) {
      fs = featureSourceFactoryHelper.openGeoToolsFeatureSource(tmFeatureType);

      SimpleFeature simpleFeature;
      SimpleFeatureBuilder simpleFeatureBuilder = new SimpleFeatureBuilder(fs.getSchema());
      if (null != completeFeature.getFid() && !completeFeature.getFid().isEmpty()) {
        simpleFeature = simpleFeatureBuilder.buildFeature(completeFeature.getFid());
        simpleFeature.getUserData().put(Hints.USE_PROVIDED_FID, Boolean.TRUE);
      } else {
        simpleFeature = simpleFeatureBuilder.buildFeature(null);
      }

      handleGeometryAttributesInput(
          tmFeatureType, appLayerSettings, completeFeature, attributesMap, application, fs);
      for (Map.Entry<String, Object> entry : attributesMap.entrySet()) {
        simpleFeature.setAttribute(entry.getKey(), entry.getValue());
      }

      if (fs instanceof SimpleFeatureStore simpleFeatureStore) {
        simpleFeatureStore.setTransaction(transaction);
        List<FeatureId> newFids = simpleFeatureStore.addFeatures(DataUtilities.collection(simpleFeature));

        transaction.commit();
        // find the created feature to return
        newFeature = getFeature(fs, ff.id(newFids.get(0)), application);
      } else {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Feature cannot be added, datasource is not editable");
      }
    } catch (RuntimeException | IOException | FactoryException e) {
      // either opening datastore, modify or transaction failed
      logger.error("Error creating new feature {}", completeFeature, e);
      String message = e.getMessage();
      if (null != e.getCause() && null != e.getCause().getMessage()) {
        message = e.getCause().getMessage();
      }
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, message, e);
    } finally {
      if (fs != null) {
        fs.getDataStore().dispose();
      }
    }
    return new ResponseEntity<>(newFeature, HttpStatus.OK);
  }

  @Transactional
  @PatchMapping(
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE,
      path = "/{fid}")
  @Timed(value = "update_feature", description = "time spent to process patch feature call")
  @Counted(value = "update_feature", description = "number of patch feature calls")
  public ResponseEntity<Serializable> patchFeature(
      @ModelAttribute AppTreeLayerNode appTreeLayerNode,
      @ModelAttribute GeoService service,
      @ModelAttribute GeoServiceLayer layer,
      @ModelAttribute Application application,
      @PathVariable String fid,
      @RequestBody Feature partialFeature) {

    checkAuthentication();

    TMFeatureType tmFeatureType = getEditableFeatureType(application, appTreeLayerNode, service, layer);
    AppLayerSettings appLayerSettings = application.getAppLayerSettings(appTreeLayerNode);

    Map<String, Object> attributesMap = partialFeature.getAttributes();

    checkFeatureHasOnlyValidAttributes(partialFeature, tmFeatureType, appLayerSettings);

    Feature patchedFeature;
    SimpleFeatureSource fs = null;
    try (Transaction transaction = new DefaultTransaction("edit")) {
      fs = featureSourceFactoryHelper.openGeoToolsFeatureSource(tmFeatureType);
      // find feature to update
      final Filter filter = ff.id(ff.featureId(fid));
      if (!fs.getFeatures(filter).isEmpty() && fs instanceof SimpleFeatureStore simpleFeatureStore) {
        handleGeometryAttributesInput(
            tmFeatureType, appLayerSettings, partialFeature, attributesMap, application, fs);
        // NOTE geotools does not report back that the feature was updated, no error === success
        simpleFeatureStore.setTransaction(transaction);
        simpleFeatureStore.modifyFeatures(
            attributesMap.keySet().toArray(new String[] {}),
            attributesMap.values().toArray(),
            filter);
        transaction.commit();
        // find the updated feature to return
        patchedFeature = getFeature(fs, filter, application);
      } else {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Feature cannot be edited, it does not exist or is not editable");
      }
    } catch (RuntimeException | IOException | FactoryException e) {
      // either opening datastore, modify or transaction failed
      logger.error("Error patching feature {}", partialFeature, e);
      String message = e.getMessage();
      if (null != e.getCause() && null != e.getCause().getMessage()) {
        message = e.getCause().getMessage();
      }
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, message, e);
    } finally {
      if (fs != null) {
        fs.getDataStore().dispose();
      }
    }
    return new ResponseEntity<>(patchedFeature, HttpStatus.OK);
  }

  @Transactional
  @DeleteMapping(path = "/{fid}")
  @Timed(value = "delete_feature", description = "time spent to process delete feature call")
  @Counted(value = "delete_feature", description = "number of delete feature calls")
  public ResponseEntity<Void> deleteFeature(
      @ModelAttribute AppTreeLayerNode appTreeLayerNode,
      @ModelAttribute GeoService service,
      @ModelAttribute GeoServiceLayer layer,
      @ModelAttribute Application application,
      @PathVariable String fid) {

    checkAuthentication();

    TMFeatureType tmFeatureType = getEditableFeatureType(application, appTreeLayerNode, service, layer);

    SimpleFeatureSource fs = null;
    try (Transaction transaction = new DefaultTransaction("delete")) {
      fs = featureSourceFactoryHelper.openGeoToolsFeatureSource(tmFeatureType);
      Filter filter = ff.id(ff.featureId(fid));
      if (fs instanceof FeatureStore<?, ?> featureStore) {
        // NOTE geotools does not report back that the feature does not exist, nor the number of
        // deleted features, no error === success
        featureStore.setTransaction(transaction);
        featureStore.removeFeatures(filter);
        transaction.commit();
      } else {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Layer cannot be edited");
      }
    } catch (IOException e) {
      // either opening datastore or commit failed
      logger.error("Error deleting feature {}", fid, e);
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
    } finally {
      if (fs != null) {
        fs.getDataStore().dispose();
      }
    }

    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  private void checkAuthentication() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    boolean isAuthenticated = authentication != null && !(authentication instanceof AnonymousAuthenticationToken);
    if (!isAuthenticated) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
    }
  }

  /**
   * Get editable feature type, throws exception if not found or not editable. Will throw a
   * {@link ResponseStatusException} if the layer does not have an editable featuretype.
   *
   * @param application the application that has the editable layer
   * @param appTreeLayerNode the layer to edit
   * @param service the service that has the layer
   * @param layer the layer to edit
   * @return the editable feature type
   */
  private TMFeatureType getEditableFeatureType(
      Application application, AppTreeLayerNode appTreeLayerNode, GeoService service, GeoServiceLayer layer) {

    if (null == layer) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cannot find layer " + appTreeLayerNode);
    }

    AppLayerSettings appLayerSettings = application.getAppLayerSettings(appTreeLayerNode);
    if (!Boolean.TRUE.equals(appLayerSettings.getEditable())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Layer is not editable");
    }

    TMFeatureType tmft = service.findFeatureTypeForLayer(layer, featureSourceRepository);
    if (null == tmft) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Layer does not have feature type");
    }
    if (!tmft.isWriteable()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Feature type is not writeable");
    }

    return tmft;
  }

  private static Feature getFeature(SimpleFeatureSource fs, Filter filter, Application application)
      throws IOException, FactoryException {
    Feature modelFeature = null;
    try (SimpleFeatureIterator feats = fs.getFeatures(filter).features()) {
      if (feats.hasNext()) {
        SimpleFeature simpleFeature = feats.next();
        modelFeature = new Feature()
            .geometry(GeometryProcessor.processGeometry(
                simpleFeature.getDefaultGeometry(),
                false,
                true,
                TransformationUtil.getTransformationToApplication(application, fs)))
            .fid(simpleFeature.getID());
        for (AttributeDescriptor att : simpleFeature.getFeatureType().getAttributeDescriptors()) {
          Object value = simpleFeature.getAttribute(att.getName());
          if (value instanceof Geometry geometry) {
            geometry = GeometryProcessor.transformGeometry(
                geometry, TransformationUtil.getTransformationToApplication(application, fs));
            value = GeometryProcessor.geometryToWKT(geometry);
          }
          modelFeature.putAttributesItem(att.getLocalName(), value);
        }
      }
    }
    return modelFeature;
  }

  /** Handle geometry attributes, essentially this is a WKT to Geometry conversion. */
  private static void handleGeometryAttributesInput(
      TMFeatureType tmFeatureType,
      AppLayerSettings appLayerSettings,
      Feature modelFeature,
      Map<String, Object> attributesMap,
      Application application,
      SimpleFeatureSource fs)
      throws FactoryException {

    final MathTransform transform = TransformationUtil.getTransformationToDataSource(application, fs);
    getNonHiddenAttributes(tmFeatureType, appLayerSettings).stream()
        .filter(attr -> TMAttributeTypeHelper.isGeometry(attr.getType()))
        .filter(attr -> modelFeature.getAttributes().containsKey(attr.getName()))
        .forEach(attr -> {
          Geometry geometry = GeometryProcessor.wktToGeometry(
              (String) modelFeature.getAttributes().get(attr.getName()));
          if (transform != null && geometry != null) {
            geometry.setSRID(Integer.parseInt(application.getCrs().substring("EPSG:".length())));
            if (logger.isTraceEnabled()) {
              logger.trace(
                  "Transforming geometry {} from {} to {}",
                  geometry.toText(),
                  geometry.getSRID(),
                  fs.getSchema()
                      .getCoordinateReferenceSystem()
                      .getIdentifiers());
            }
            geometry = GeometryProcessor.transformGeometry(geometry, transform);
          }
          attributesMap.put(attr.getName(), geometry);
        });
  }
}
