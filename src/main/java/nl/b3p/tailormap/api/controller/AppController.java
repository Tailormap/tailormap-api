/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import static nl.b3p.tailormap.api.util.ErrorResponseBuilder.badRequest;
import static nl.b3p.tailormap.api.util.ErrorResponseBuilder.forbidden;
import static nl.b3p.tailormap.api.util.ErrorResponseBuilder.notFound;

import java.io.Serializable;
import nl.b3p.tailormap.api.persistence.Application;
import nl.b3p.tailormap.api.persistence.Configuration;
import nl.b3p.tailormap.api.repository.ApplicationRepository;
import nl.b3p.tailormap.api.repository.ConfigurationRepository;
import nl.b3p.tailormap.api.security.AuthorizationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/app")
public class AppController {
  private final ApplicationRepository applicationRepository;
  private final ConfigurationRepository configurationRepository;
  private final AuthorizationService authorizationService;

  @Value("${tailormap-api.apiVersion}")
  private String apiVersion;

  public AppController(
      ApplicationRepository applicationRepository,
      ConfigurationRepository configurationRepository,
      AuthorizationService authorizationService) {
    this.applicationRepository = applicationRepository;
    this.configurationRepository = configurationRepository;
    this.authorizationService = authorizationService;
  }

  @GetMapping
  public ResponseEntity<Serializable> get(
      @RequestParam(required = false) Long appId, @RequestParam(required = false) String name) {

    if (appId != null && name != null) {
      return badRequest();
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
      return notFound(notFoundMessage);
    }

    if (!authorizationService.mayUserRead(app)) {
      return forbidden();
    }

    return ResponseEntity.ok(app.toAppResponse().apiVersion(apiVersion));
  }
}
