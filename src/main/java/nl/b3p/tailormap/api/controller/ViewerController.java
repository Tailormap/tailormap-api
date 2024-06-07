/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package nl.b3p.tailormap.api.controller;

import static nl.b3p.tailormap.api.util.Constants.UUID_REGEX;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;

import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import java.util.Map;
import java.util.UUID;
import nl.b3p.tailormap.api.annotation.AppRestController;
import nl.b3p.tailormap.api.persistence.Application;
import nl.b3p.tailormap.api.persistence.Configuration;
import nl.b3p.tailormap.api.persistence.Upload;
import nl.b3p.tailormap.api.persistence.helper.ApplicationHelper;
import nl.b3p.tailormap.api.repository.ApplicationRepository;
import nl.b3p.tailormap.api.repository.ConfigurationRepository;
import nl.b3p.tailormap.api.repository.UploadRepository;
import nl.b3p.tailormap.api.security.AuthorizationService;
import nl.b3p.tailormap.api.viewer.model.AppStyling;
import nl.b3p.tailormap.api.viewer.model.MapResponse;
import nl.b3p.tailormap.api.viewer.model.ViewerResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.server.ResponseStatusException;

@AppRestController
public class ViewerController {

  private final ConfigurationRepository configurationRepository;
  private final ApplicationRepository applicationRepository;
  private final ApplicationHelper applicationHelper;
  private final AuthorizationService authorizationService;
  private final UploadRepository uploadRepository;

  public ViewerController(
      ConfigurationRepository configurationRepository,
      ApplicationRepository applicationRepository,
      ApplicationHelper applicationHelper,
      AuthorizationService authorizationService,
      UploadRepository uploadRepository) {
    this.configurationRepository = configurationRepository;
    this.applicationRepository = applicationRepository;
    this.applicationHelper = applicationHelper;
    this.authorizationService = authorizationService;
    this.uploadRepository = uploadRepository;
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
    if (!this.authorizationService.mayUserRead(app)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
    return viewer(app, ViewerResponse.KindEnum.APP);
  }

  // We can't parametrize viewerKind (/app/ and /service/) because they'd clash with /api/admin/...
  // so name the paths separately
  @GetMapping(
      path = {
        "${tailormap-api.base-path}/app/{viewerName}",
        "${tailormap-api.base-path}/service/{viewerName}"
      })
  @Timed(value = "get_named_app", description = "Get named app")
  @Counted(value = "get_named_app", description = "Count of get named app")
  public ViewerResponse viewer(
      @ModelAttribute Application app, @ModelAttribute ViewerResponse.KindEnum viewerKind) {
    ViewerResponse viewerResponse = app.getViewerResponse().kind(viewerKind);

    AppStyling styling = viewerResponse.getStyling();
    if (styling != null && styling.getLogo() != null && styling.getLogo().matches(UUID_REGEX)) {
      uploadRepository
          .findByIdAndCategory(UUID.fromString(styling.getLogo()), Upload.CATEGORY_APP_LOGO)
          .ifPresentOrElse(
              logo -> {
                // Modify entity for viewer but don't save so changes aren't persisted
                styling.setLogo(
                    linkTo(
                            UploadsController.class,
                            Map.of(
                                "id",
                                logo.getId().toString(),
                                "category",
                                logo.getCategory(),
                                "filename",
                                logo.getFilename()))
                        .toString());
              },
              () -> styling.setLogo(null));
    }
    return viewerResponse;
  }

  @GetMapping(
      path = {
        "${tailormap-api.base-path}/app/{viewerName}/map",
        "${tailormap-api.base-path}/service/{viewerName}/map"
      })
  public MapResponse map(@ModelAttribute Application app) {
    return applicationHelper.toMapResponse(app);
  }
}
