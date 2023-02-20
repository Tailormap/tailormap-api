/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import java.lang.invoke.MethodHandles;
import nl.b3p.tailormap.api.annotation.AppRestController;
import nl.b3p.tailormap.api.persistence.Application;
import nl.b3p.tailormap.api.persistence.GeoService;
import nl.b3p.tailormap.api.persistence.TMFeatureSource;
import nl.b3p.tailormap.api.persistence.TMFeatureType;
import nl.b3p.tailormap.api.persistence.json.AppLayerRef;
import nl.b3p.tailormap.api.persistence.json.GeoServiceDefaultLayerSettings;
import nl.b3p.tailormap.api.persistence.json.GeoServiceLayer;
import nl.b3p.tailormap.api.persistence.json.GeoServiceLayerSettings;
import nl.b3p.tailormap.api.repository.ApplicationRepository;
import nl.b3p.tailormap.api.repository.FeatureSourceRepository;
import nl.b3p.tailormap.api.repository.GeoServiceRepository;
import nl.b3p.tailormap.api.security.AuthorizationService;
import nl.b3p.tailormap.api.viewer.model.ErrorResponse;
import nl.b3p.tailormap.api.viewer.model.RedirectResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(annotations = AppRestController.class)
public class AppRestControllerAdvice {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final ApplicationRepository applicationRepository;
  private final GeoServiceRepository geoServiceRepository;
  private final FeatureSourceRepository featureSourceRepository;
  private final AuthorizationService authorizationService;

  public AppRestControllerAdvice(
      ApplicationRepository applicationRepository,
      GeoServiceRepository geoServiceRepository,
      FeatureSourceRepository featureSourceRepository,
      AuthorizationService authorizationService) {
    this.applicationRepository = applicationRepository;
    this.geoServiceRepository = geoServiceRepository;
    this.featureSourceRepository = featureSourceRepository;
    this.authorizationService = authorizationService;
  }

  @InitBinder
  protected void initBinder(WebDataBinder binder) {
    binder.setAllowedFields("appId", "appLayerId");
  }

  @ExceptionHandler(ResponseStatusException.class)
  protected ResponseEntity<?> handleResponseStatusException(ResponseStatusException ex) {
    if (HttpStatus.UNAUTHORIZED.equals(ex.getStatus())) {
      return ResponseEntity.status(ex.getStatus())
          .contentType(MediaType.APPLICATION_JSON)
          .body(new RedirectResponse());
    }
    return ResponseEntity.status(ex.getStatus())
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            new ErrorResponse()
                .message(ex.getReason() != null ? ex.getReason() : ex.getStatus().getReasonPhrase())
                .code(ex.getRawStatusCode()));
  }

  @ModelAttribute
  public Application populateApplication(@PathVariable(required = false) Long appId) {
    if (appId == null) {
      // No binding required for AppController
      return null;
    }
    final Application application = applicationRepository.findById(appId).orElse(null);
    if (application == null) {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND, "Application with id " + appId + " not found");
    }
    if (!this.authorizationService.mayUserRead(application)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
    return application;
  }

  @ModelAttribute
  public AppLayerRef populateAppLayerRef(
      @ModelAttribute Application application, @PathVariable(required = false) Long appLayerId) {
    if (appLayerId == null) {
      // No binding
      return null;
    }

    final AppLayerRef ref =
        application
            .getAllAppLayerRefs()
            .filter(r -> r.getId().equals(appLayerId))
            .findFirst()
            .orElse(null);
    if (ref == null) {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND, "Application layer with id " + appLayerId + " not found");
    }

    // TODO
    //    if (!this.authorizationService.mayUserRead(applicationLayer, application)) {
    //      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
    //    }
    return ref;
  }

  @ModelAttribute
  public GeoService populateGeoService(@ModelAttribute AppLayerRef appLayerRef) {
    if (appLayerRef == null) {
      // No binding
      return null;
    }
    if (appLayerRef.getServiceId() == null) {
      return null;
    }
    return geoServiceRepository.findById(appLayerRef.getServiceId()).orElse(null);
  }

  @ModelAttribute
  public GeoServiceLayer populateGeoServiceLayer(
      @ModelAttribute AppLayerRef appLayerRef, @ModelAttribute GeoService service) {
    if (service == null) {
      // No binding
      return null;
    }
    return service.getLayers().stream()
        .filter(l -> appLayerRef.getLayerName().equals(l.getName()))
        .findFirst()
        .orElse(null);
  }

  @ModelAttribute
  @Transactional
  public TMFeatureType populateFeatureType(
      @ModelAttribute AppLayerRef appLayerRef,
      @ModelAttribute GeoService service,
      @ModelAttribute GeoServiceLayer layer) {
    if (layer == null) {
      // No binding
      return null;
    }
    GeoServiceDefaultLayerSettings defaultLayerSettings =
        service.getSettings().getDefaultLayerSettings();
    GeoServiceLayerSettings layerSettings = service.getLayerSettings(appLayerRef.getLayerName());

    String featureTypeName;

    Long featureSourceId = null;

    if (layerSettings != null && layerSettings.getFeatureType() != null) {
      featureTypeName = layerSettings.getFeatureType().getFeatureTypeName();
      featureSourceId = layerSettings.getFeatureType().getFeatureSourceId();
    } else {
      featureTypeName = layer.getName();
    }

    if (featureSourceId == null
        && defaultLayerSettings != null
        && defaultLayerSettings.getFeatureType() != null) {
      featureSourceId = defaultLayerSettings.getFeatureType().getFeatureSourceId();
    }

    if (featureTypeName == null) {
      return null;
    }

    TMFeatureSource tmfs;
    if (featureSourceId == null) {
      tmfs = featureSourceRepository.findByLinkedServiceId(service.getId()).orElse(null);
    } else {
      tmfs = featureSourceRepository.findById(featureSourceId).orElse(null);
    }

    if (tmfs == null) {
      return null;
    }
    TMFeatureType tmft =
        tmfs.getFeatureTypes().stream()
            .filter(ft -> featureTypeName.equals(ft.getName()))
            .findFirst()
            .orElse(null);

    if (tmft == null) {
      String[] split = featureTypeName.split(":", 2);
      if (split.length == 2) {
        String shortFeatureTypeName = split[1];
        tmft =
            tmfs.getFeatureTypes().stream()
                .filter(ft -> shortFeatureTypeName.equals(ft.getName()))
                .findFirst()
                .orElse(null);
        logger.debug(
            "Did not find feature type with full name \"{}\", using \"{}\" of feature source {}",
            featureTypeName,
            shortFeatureTypeName,
            tmfs);
      }
    }
    return tmft;
  }
}
