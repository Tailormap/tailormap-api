/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import nl.b3p.tailormap.api.annotation.AppRestController;
import nl.b3p.tailormap.api.persistence.Application;
import nl.b3p.tailormap.api.persistence.GeoService;
import nl.b3p.tailormap.api.persistence.helper.ApplicationHelper;
import nl.b3p.tailormap.api.persistence.json.AppLayerRef;
import nl.b3p.tailormap.api.persistence.json.GeoServiceLayer;
import nl.b3p.tailormap.api.repository.ApplicationRepository;
import nl.b3p.tailormap.api.repository.GeoServiceRepository;
import nl.b3p.tailormap.api.viewer.model.ErrorResponse;
import nl.b3p.tailormap.api.viewer.model.RedirectResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(annotations = AppRestController.class)
public class AppRestControllerAdvice {
  private final ApplicationRepository applicationRepository;
  private final GeoServiceRepository geoServiceRepository;
  private final ApplicationHelper applicationHelper;
  // private final AuthorizationService authorizationService;

  public AppRestControllerAdvice(
      ApplicationRepository applicationRepository,
      GeoServiceRepository geoServiceRepository,
      ApplicationHelper applicationHelper) {
    this.applicationRepository = applicationRepository;
    this.geoServiceRepository = geoServiceRepository;
    this.applicationHelper = applicationHelper;
  }

  @InitBinder
  protected void initBinder(WebDataBinder binder) {
    // WARNING! These fields must NOT match properties of ModelAttribute classes, otherwise they
    // will be overwritten (for instance GeoServiceLayer.name might be set to the app name)
    binder.setAllowedFields("viewerKind", "viewerName", "appLayerId", "base", "projection");
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
  public Application populateApplication(
      @PathVariable(required = false) String viewerKind,
      @PathVariable(required = false) String viewerName,
      @RequestParam(required = false) String base,
      @RequestParam(required = false) String projection) {
    if (viewerKind == null || viewerName == null) {
      // No binding required for ViewerController.defaultApp()
      return null;
    }

    Application app;
    if ("app".equals(viewerKind)) {
      app = applicationRepository.findByName(viewerName);
      if (app == null) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND);
      }
    } else if ("service".equals(viewerKind)) {
      GeoService service = geoServiceRepository.findById(viewerName).orElse(null);

      if (service == null) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND);
      }

      // TODO: skip this check for users with admin role
      if (!service.isPublished()) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND);
      }
      app = applicationHelper.getServiceApplication(base, projection, service);
    } else {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    }

    // TODO check authorization for app
    //    if (!this.authorizationService.mayUserRead(application)) {
    //      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    //    }
    return app;
  }

  @ModelAttribute
  public AppLayerRef populateAppLayerRef(
      @ModelAttribute Application app, @PathVariable(required = false) String appLayerId) {
    if (app == null || appLayerId == null) {
      // No binding
      return null;
    }

    final AppLayerRef ref =
        app.getAllAppLayerRefs().filter(r -> r.getId().equals(appLayerId)).findFirst().orElse(null);
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
}
