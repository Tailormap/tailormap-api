/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package nl.b3p.tailormap.api.controller;

import nl.b3p.tailormap.api.annotation.AppRestController;
import nl.b3p.tailormap.api.persistence.Application;
import nl.b3p.tailormap.api.persistence.Configuration;
import nl.b3p.tailormap.api.persistence.helper.ApplicationHelper;
import nl.b3p.tailormap.api.repository.ApplicationRepository;
import nl.b3p.tailormap.api.repository.ConfigurationRepository;
import nl.b3p.tailormap.api.viewer.model.MapResponse;
import nl.b3p.tailormap.api.viewer.model.ViewerResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

@AppRestController
public class ViewerController {

  private final ConfigurationRepository configurationRepository;
  private final ApplicationRepository applicationRepository;
  private final ApplicationHelper applicationHelper;

  public ViewerController(
      ConfigurationRepository configurationRepository,
      ApplicationRepository applicationRepository,
      ApplicationHelper applicationHelper) {
    this.configurationRepository = configurationRepository;
    this.applicationRepository = applicationRepository;
    this.applicationHelper = applicationHelper;
  }

  @GetMapping(path = "${tailormap-api.base-path}/app")
  public ViewerResponse defaultApp() {
    String defaultAppName = configurationRepository.get(Configuration.DEFAULT_APP);
    Application app = applicationRepository.findByName(defaultAppName);
    if (app == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    // TODO check authorization for app
    return viewer(app, ViewerResponse.KindEnum.APP.getValue());
  }

  @GetMapping(path = "${tailormap-api.base-path}/{viewerKind}/{viewerName}")
  public ViewerResponse viewer(@ModelAttribute Application app, @PathVariable String viewerKind) {
    return app.getViewerResponse().kind(ViewerResponse.KindEnum.fromValue(viewerKind));
  }

  @GetMapping(path = "${tailormap-api.base-path}/{viewerKind}/{viewerName}/map")
  public MapResponse map(@ModelAttribute Application app) {
    return applicationHelper.toMapResponse(app);
  }
}
