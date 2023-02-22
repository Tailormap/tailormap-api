/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import java.io.Serializable;
import nl.b3p.tailormap.api.persistence.Application;
import nl.b3p.tailormap.api.persistence.Configuration;
import nl.b3p.tailormap.api.persistence.GeoService;
import nl.b3p.tailormap.api.repository.ApplicationRepository;
import nl.b3p.tailormap.api.repository.ConfigurationRepository;
import nl.b3p.tailormap.api.repository.GeoServiceRepository;
import nl.b3p.tailormap.api.security.AuthorizationService;
import nl.b3p.tailormap.api.viewer.model.AppResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping(path = "${tailormap-api.base-path}/app")
public class AppController {
  private final ApplicationRepository applicationRepository;
  private final GeoServiceRepository geoServiceRepository;
  private final ConfigurationRepository configurationRepository;
  private final AuthorizationService authorizationService;

  public AppController(
      ApplicationRepository applicationRepository,
      GeoServiceRepository geoServiceRepository,
      ConfigurationRepository configurationRepository,
      AuthorizationService authorizationService) {
    this.applicationRepository = applicationRepository;
    this.geoServiceRepository = geoServiceRepository;
    this.configurationRepository = configurationRepository;
    this.authorizationService = authorizationService;
  }

  @GetMapping
  public ResponseEntity<Serializable> get(
      @RequestParam(required = false) Long appId, @RequestParam(required = false) String name) {

    if (appId != null && name != null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    }

    // TODO: Just for test, make proper API later
    if (name != null && name.startsWith("service")) {
      String[] parts = name.split("-", 2);
      GeoService service = geoServiceRepository.findById(Long.parseLong(parts[1])).orElse(null);
      if (service != null && service.isPublished()) {
        return ResponseEntity.ok(
            new AppResponse().id(1000 + service.getId()).name(name).title(service.getTitle()));
      }
    }

    Application app = null;
    String notFoundMessage;

    if (appId != null) {
      notFoundMessage = String.format("Application with id %d not found", appId);
      app = applicationRepository.findById(appId).orElse(null);
    } else if (name != null) {
      notFoundMessage = String.format("Application \"%s\" not found", name);
      app = applicationRepository.findByName(name);
    } else {
      notFoundMessage = "No default application configured";
      String defaultName =
          configurationRepository
              .findByKey(Configuration.DEFAULT_APP)
              .map(Configuration::getValue)
              .orElse(null);
      if (defaultName != null) {
        notFoundMessage = "Default application not found";
        app = applicationRepository.findByName(defaultName);
      }
    }

    if (app == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, notFoundMessage);
    }

    if (!authorizationService.mayUserRead(app)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    return ResponseEntity.ok(app.toAppResponse());
  }
}
