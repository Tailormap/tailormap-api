/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.controller;

import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Metrics;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.server.ResponseStatusException;
import org.tailormap.api.annotation.AppRestController;
import org.tailormap.api.persistence.Application;
import org.tailormap.api.persistence.Configuration;
import org.tailormap.api.persistence.Upload;
import org.tailormap.api.persistence.helper.ApplicationHelper;
import org.tailormap.api.persistence.helper.UploadHelper;
import org.tailormap.api.repository.ApplicationRepository;
import org.tailormap.api.repository.ConfigurationRepository;
import org.tailormap.api.security.AuthorisationService;
import org.tailormap.api.util.Constants;
import org.tailormap.api.viewer.model.AppStyling;
import org.tailormap.api.viewer.model.MapResponse;
import org.tailormap.api.viewer.model.ViewerResponse;

@AppRestController
public class ViewerController implements Constants {

  private final ConfigurationRepository configurationRepository;
  private final ApplicationRepository applicationRepository;
  private final ApplicationHelper applicationHelper;
  private final AuthorisationService authorisationService;
  private final UploadHelper uploadHelper;

  public ViewerController(
      ConfigurationRepository configurationRepository,
      ApplicationRepository applicationRepository,
      ApplicationHelper applicationHelper,
      AuthorisationService authorisationService,
      UploadHelper uploadHelper) {
    this.configurationRepository = configurationRepository;
    this.applicationRepository = applicationRepository;
    this.applicationHelper = applicationHelper;
    this.authorisationService = authorisationService;
    this.uploadHelper = uploadHelper;
  }

  @GetMapping(path = "${tailormap-api.base-path}/app")
  @Timed(value = "get_default_app", description = "Get default app")
  @Counted(value = "get_default_app", description = "Count of get default app")
  public ViewerResponse defaultApp() {
    String defaultAppName = configurationRepository.get(Configuration.DEFAULT_APP);
    Application app = applicationRepository.findByName(defaultAppName);
    if (app == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    if (!this.authorisationService.userAllowedToViewApplication(app)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
    return viewer(app, ViewerResponse.KindEnum.APP);
  }

  // We can't parametrize viewerKind (/app/ and /service/) because they'd clash with /api/admin/...
  // so name the paths separately
  @GetMapping(
      path = {"${tailormap-api.base-path}/app/{viewerName}", "${tailormap-api.base-path}/service/{viewerName}"})
  @Timed(value = "get_named_app", description = "Get named app")
  @Counted(value = "get_named_app", description = "Count of get named app")
  public ViewerResponse viewer(@ModelAttribute Application app, @ModelAttribute ViewerResponse.KindEnum viewerKind) {
    ViewerResponse viewerResponse = app.getViewerResponse().kind(viewerKind);

    AppStyling styling = viewerResponse.getStyling();
    if (styling != null) {
      styling.setLogo(uploadHelper.getUrlForImage(styling.getLogo(), Upload.CATEGORY_APP_LOGO));
    }

    // count/increment the number of times this viewer has been requested
    Metrics.counter(
            METRICS_APP_REQUEST_COUNTER_NAME,
            METRICS_TYPE_TAG,
            viewerKind.name().toLowerCase(Locale.getDefault()),
            METRICS_NAME_TAG,
            app.getName(),
            METRICS_ID_TAG,
            app.getId().toString())
        .increment();
    // TODO cleanup
    //  the above will produce output as follows after calling this endpoint a few times:
    //  curl http://localhost:8080/api/app/austria
    //  curl http://localhost:8080/api/service/snapshot-geoserver
    //  curl http://localhost:8080/api/app/default
    //
    //  # HELP tailormap_app_request_total
    //  # TYPE tailormap_app_request_total counter
    //
    // tailormap_app_request_total{appId="1",appName="default",appType="app",application="tailormap-api",hostname="localhost"} 8.0
    //
    // tailormap_app_request_total{appId="2",appName="snapshot-geoserver",appType="service",application="tailormap-api",hostname="localhost"} 5.0
    //
    // tailormap_app_request_total{appId="5",appName="austria",appType="app",application="tailormap-api",hostname="localhost"} 1.0

    return viewerResponse;
  }

  @GetMapping(
      path = {
        "${tailormap-api.base-path}/app/{viewerName}/map",
        "${tailormap-api.base-path}/service/{viewerName}/map"
      })
  public MapResponse map(@ModelAttribute Application app) {
    MapResponse mapResponse = applicationHelper.toMapResponse(app);
    mapResponse.getAppLayers().stream()
        .filter(l ->
            l.getLegendImageUrl() != null && l.getLegendImageUrl().matches(UUID_REGEX))
        .forEach(l -> l.setLegendImageUrl(
            uploadHelper.getUrlForImage(l.getLegendImageUrl(), Upload.CATEGORY_LEGEND)));
    return mapResponse;
  }
}
