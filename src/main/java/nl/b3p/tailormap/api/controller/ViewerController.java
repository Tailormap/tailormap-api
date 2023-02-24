/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package nl.b3p.tailormap.api.controller;

import java.util.List;
import javax.persistence.EntityManager;
import nl.b3p.tailormap.api.persistence.Application;
import nl.b3p.tailormap.api.persistence.Configuration;
import nl.b3p.tailormap.api.persistence.GeoService;
import nl.b3p.tailormap.api.persistence.helper.ApplicationHelper;
import nl.b3p.tailormap.api.repository.ApplicationRepository;
import nl.b3p.tailormap.api.repository.ConfigurationRepository;
import nl.b3p.tailormap.api.repository.GeoServiceRepository;
import nl.b3p.tailormap.api.viewer.model.MapResponse;
import nl.b3p.tailormap.api.viewer.model.ViewerResponse;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class ViewerController {

  private final ConfigurationRepository configurationRepository;
  private final ApplicationRepository applicationRepository;
  private final ApplicationHelper applicationHelper;
  private final GeoServiceRepository geoServiceRepository;
  private final EntityManager entityManager;

  // TODO use ControllerAdvice to reduce code duplication

  public ViewerController(
      ConfigurationRepository configurationRepository,
      ApplicationRepository applicationRepository,
      ApplicationHelper applicationHelper,
      GeoServiceRepository geoServiceRepository,
      EntityManager entityManager) {
    this.configurationRepository = configurationRepository;
    this.applicationRepository = applicationRepository;
    this.applicationHelper = applicationHelper;
    this.geoServiceRepository = geoServiceRepository;
    this.entityManager = entityManager;
  }

  @GetMapping(path = "${tailormap-api.base-path}/app")
  public ViewerResponse defaultApp() {
    String defaultAppName = configurationRepository.get(Configuration.DEFAULT_APP);
    return app(defaultAppName);
  }

  @NonNull
  private Application getValidatedApplication(String name) {
    if (name == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    Application app = applicationRepository.findByName(name);
    if (app == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    // TODO check authorization
    return app;
  }

  @GetMapping(path = "${tailormap-api.base-path}/app/{name}")
  public ViewerResponse app(@PathVariable String name) {
    return getValidatedApplication(name).getViewerResponse();
  }

  @GetMapping(path = "${tailormap-api.base-path}/app/{name}/map")
  public MapResponse appMap(@PathVariable String name) {
    return applicationHelper.toMapResponse(getValidatedApplication(name));
  }

  @NonNull
  private Pair<GeoService, Application> getValidatedPublishedServiceWithBase(String name) {
    GeoService service = geoServiceRepository.findByName(name);
    if (service == null || !service.isPublished()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    // TODO: check authorization for service

    // TODO: test als geen default base app of ingesteld geen baseApp in publishing settings
    Application base =
        service.getDetachedBaseApp(configurationRepository, applicationRepository, entityManager);

    // TODO: check authorization for base app too

    return Pair.of(service, base);
  }

  @GetMapping(path = "${tailormap-api.base-path}/service/{name}")
  public ViewerResponse service(@PathVariable String name) {
    Pair<GeoService, Application> serviceApplicationPair =
        getValidatedPublishedServiceWithBase(name);
    GeoService service = serviceApplicationPair.getLeft();
    Application base = serviceApplicationPair.getRight();

    ViewerResponse response = new ViewerResponse();
    if (base != null) {
      response = base.getViewerResponse();

      // Reduce projections, set default as first projection?
    }

    response
        .kind(ViewerResponse.KindEnum.SERVICE)
        .name(service.getName())
        .title(service.getTitle())
        // TODO: check service.settings.publishing for base apps, only include apps that exist
        .baseViewers(base != null ? List.of(base.getName(), "none") : List.of("none"));
    return response;
  }

  @Transactional // XXX needed to fetch feature types for layers...
  @GetMapping(path = "${tailormap-api.base-path}/service/{name}/map")
  public MapResponse serviceMap(@PathVariable String name) {
    Pair<GeoService, Application> serviceApplicationPair =
        getValidatedPublishedServiceWithBase(name);
    GeoService service = serviceApplicationPair.getLeft();
    Application base = serviceApplicationPair.getRight();
    return applicationHelper.toMapResponse(service, base);
  }
}
