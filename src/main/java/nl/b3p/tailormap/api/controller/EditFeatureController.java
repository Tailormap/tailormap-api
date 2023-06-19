/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import io.micrometer.core.annotation.Timed;
import java.io.IOException;
import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import nl.b3p.tailormap.api.annotation.AppRestController;
import nl.b3p.tailormap.api.geotools.featuresources.FeatureSourceFactoryHelper;
import nl.b3p.tailormap.api.persistence.GeoService;
import nl.b3p.tailormap.api.persistence.TMFeatureType;
import nl.b3p.tailormap.api.persistence.json.AppTreeLayerNode;
import nl.b3p.tailormap.api.persistence.json.GeoServiceLayer;
import nl.b3p.tailormap.api.repository.FeatureSourceRepository;
import nl.b3p.tailormap.api.util.Constants;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.FeatureStore;
import org.geotools.data.Transaction;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.util.factory.GeoTools;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

@AppRestController
@Validated
@RequestMapping(
    path =
        "${tailormap-api.base-path}/{viewerKind}/{viewerName}/layer/{appLayerId}/edit/feature/{fid}",
    produces = MediaType.APPLICATION_JSON_VALUE)
public class EditFeatureController implements Constants {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final FeatureSourceFactoryHelper featureSourceFactoryHelper;
  private final FilterFactory2 ff =
      CommonFactoryFinder.getFilterFactory2(GeoTools.getDefaultHints());
  private final FeatureSourceRepository featureSourceRepository;

  public EditFeatureController(
      FeatureSourceFactoryHelper featureSourceFactoryHelper,
      FeatureSourceRepository featureSourceRepository) {
    this.featureSourceFactoryHelper = featureSourceFactoryHelper;
    this.featureSourceRepository = featureSourceRepository;
  }

  @PostMapping
  @Timed(value = "create_feature", description = "time spent to process create feature call")
  public ResponseEntity<Serializable> createFeature() {
    checkAuthentication();
    throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Not implemented");
  }

  @PatchMapping
  @Timed(value = "update_feature", description = "time spent to process patch feature call")
  public ResponseEntity<Serializable> patchFeature() {
    checkAuthentication();
    throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Not implemented");
  }

  @Transactional
  @DeleteMapping
  @Timed(value = "delete_feature", description = "time spent to process delete feature call")
  public ResponseEntity<Void> deleteFeature(
      @ModelAttribute AppTreeLayerNode appTreeLayerNode,
      @ModelAttribute GeoService service,
      @ModelAttribute GeoServiceLayer layer,
      @PathVariable String fid) {

    checkAuthentication();

    TMFeatureType tmFeatureType = getFeatureType(appTreeLayerNode, service, layer);

    SimpleFeatureSource fs = null;
    try (Transaction transaction = new DefaultTransaction("delete")) {
      fs = featureSourceFactoryHelper.openGeoToolsFeatureSource(tmFeatureType);
      Filter filter = ff.id(ff.featureId(fid));
      if (fs instanceof FeatureStore) {
        // NOTE geotools does not report back that the feature does not exist, nor the number of
        // deleted features
        // no error === success
        ((FeatureStore<?, ?>) fs).setTransaction(transaction);
        ((FeatureStore<?, ?>) fs).removeFeatures(filter);
        transaction.commit();
      } else {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Layer cannot be edited");
      }
    } catch (IOException e) {
      // either opening datastore or commit failed
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
    boolean isAuthenticated =
        authentication != null && !(authentication instanceof AnonymousAuthenticationToken);
    if (!isAuthenticated) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
    }
  }

  private TMFeatureType getFeatureType(
      AppTreeLayerNode appTreeLayerNode, GeoService service, GeoServiceLayer layer) {
    if (null == layer) {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND, "Cannot find layer " + appTreeLayerNode);
    }

    TMFeatureType tmft = service.findFeatureTypeForLayer(layer, featureSourceRepository);
    if (null == tmft) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Layer does not have feature type");
    }
    return tmft;
  }
}
