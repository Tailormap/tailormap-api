/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import jakarta.servlet.http.HttpServletRequest;
import nl.b3p.tailormap.api.annotation.AppRestController;
import nl.b3p.tailormap.api.persistence.Application;
import nl.b3p.tailormap.api.persistence.GeoService;
import nl.b3p.tailormap.api.persistence.helper.ApplicationHelper;
import nl.b3p.tailormap.api.persistence.json.AppTreeLayerNode;
import nl.b3p.tailormap.api.persistence.json.GeoServiceLayer;
import nl.b3p.tailormap.api.repository.ApplicationRepository;
import nl.b3p.tailormap.api.repository.GeoServiceRepository;
import nl.b3p.tailormap.api.security.AuthorizationService;
import nl.b3p.tailormap.api.viewer.model.ErrorResponse;
import nl.b3p.tailormap.api.viewer.model.RedirectResponse;
import nl.b3p.tailormap.api.viewer.model.ViewerResponse;
import org.springframework.beans.factory.annotation.Value;
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
  private final AuthorizationService authorizationService;

  @Value("${tailormap-api.base-path}")
  private String basePath;

  public AppRestControllerAdvice(
      ApplicationRepository applicationRepository,
      GeoServiceRepository geoServiceRepository,
      ApplicationHelper applicationHelper,
      AuthorizationService authorizationService) {
    this.applicationRepository = applicationRepository;
    this.geoServiceRepository = geoServiceRepository;
    this.applicationHelper = applicationHelper;
    this.authorizationService = authorizationService;
  }

  @InitBinder
  protected void initBinder(WebDataBinder binder) {
    // WARNING! These fields must NOT match properties of ModelAttribute classes, otherwise they
    // will be overwritten (for instance GeoServiceLayer.name might be set to the app name)
    binder.setAllowedFields("viewerName", "appLayerId", "base", "projection");
  }

  @ExceptionHandler(ResponseStatusException.class)
  protected ResponseEntity<?> handleResponseStatusException(ResponseStatusException ex) {
    if (HttpStatus.UNAUTHORIZED.equals(ex.getStatusCode())) {
      return ResponseEntity.status(ex.getStatusCode())
          .contentType(MediaType.APPLICATION_JSON)
          .body(new RedirectResponse());
    }
    return ResponseEntity.status(ex.getStatusCode())
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            new ErrorResponse()
                .message(ex.getReason() != null ? ex.getReason() : ex.getBody().getTitle())
                .code(ex.getStatusCode().value()));
  }

  @ModelAttribute
  public ViewerResponse.KindEnum populateViewerKind(HttpServletRequest request) {
    if (request.getServletPath().startsWith(basePath + "/app/")) {
      return ViewerResponse.KindEnum.APP;
    } else if (request.getServletPath().startsWith(basePath + "/service/")) {
      return ViewerResponse.KindEnum.SERVICE;
    } else {
      return null;
    }
  }

  @ModelAttribute
  public Application populateApplication(
      @ModelAttribute ViewerResponse.KindEnum viewerKind,
      @PathVariable(required = false) String viewerName,
      @RequestParam(required = false) String base,
      @RequestParam(required = false) String projection) {
    if (viewerKind == null || viewerName == null) {
      // No binding required for ViewerController.defaultApp()
      return null;
    }

    Application app;
    if (viewerKind == ViewerResponse.KindEnum.APP) {
      app = applicationRepository.findByName(viewerName);
      if (app == null) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND);
      }
    } else if (viewerKind == ViewerResponse.KindEnum.SERVICE) {
      GeoService service = geoServiceRepository.findById(viewerName).orElse(null);

      if (service == null) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND);
      }

      if (!authorizationService.mayUserRead(service)) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
      }

      // TODO: skip this check for users with admin role
      if (!service.isPublished()) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND);
      }
      app = applicationHelper.getServiceApplication(base, projection, service);
    } else {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    }

    if (!this.authorizationService.mayUserRead(app)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
    return app;
  }

  @ModelAttribute
  public AppTreeLayerNode populateAppTreeLayerNode(
      @ModelAttribute Application app, @PathVariable(required = false) String appLayerId) {
    if (app == null || appLayerId == null) {
      // No binding
      return null;
    }

    final AppTreeLayerNode layerNode =
        app.getAllAppTreeLayerNode()
            .filter(r -> r.getId().equals(appLayerId))
            .findFirst()
            .orElse(null);
    if (layerNode == null) {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND, "Application layer with id " + appLayerId + " not found");
    }

    // TODO
    //    if (!this.authorizationService.mayUserRead(applicationLayer, application)) {
    //      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
    //    }
    return layerNode;
  }

  @ModelAttribute
  public GeoService populateGeoService(@ModelAttribute AppTreeLayerNode appTreeLayerNode) {
    if (appTreeLayerNode == null) {
      // No binding
      return null;
    }
    if (appTreeLayerNode.getServiceId() == null) {
      return null;
    }
    GeoService service =
        geoServiceRepository.findById(appTreeLayerNode.getServiceId()).orElse(null);
    if (service != null && !authorizationService.mayUserRead(service)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
    return service;
  }

  @ModelAttribute
  public GeoServiceLayer populateGeoServiceLayer(
      @ModelAttribute AppTreeLayerNode appTreeLayerNode, @ModelAttribute GeoService service) {
    if (service == null) {
      // No binding
      return null;
    }
    GeoServiceLayer layer =
        service.getLayers().stream()
            .filter(l -> appTreeLayerNode.getLayerName().equals(l.getName()))
            .findFirst()
            .orElse(null);

    if (layer != null && !authorizationService.mayUserRead(service, layer)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }

    return layer;
  }
}
