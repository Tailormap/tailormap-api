/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package nl.b3p.tailormap.api.controller.admin;

import jakarta.servlet.http.HttpServletResponse;
import nl.b3p.tailormap.api.persistence.GeoService;
import nl.b3p.tailormap.api.repository.GeoServiceRepository;
import org.springframework.context.ApplicationContext;
import org.springframework.data.rest.core.event.BeforeSaveEvent;
import org.springframework.data.rest.webmvc.support.RepositoryEntityLinks;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class GeoServiceAdminController {
  private final GeoServiceRepository geoServiceRepository;
  private final ApplicationContext applicationContext;
  private final RepositoryEntityLinks repositoryEntityLinks;

  public GeoServiceAdminController(
      GeoServiceRepository geoServiceRepository,
      ApplicationContext applicationContext,
      RepositoryEntityLinks repositoryEntityLinks) {
    this.geoServiceRepository = geoServiceRepository;
    this.applicationContext = applicationContext;
    this.repositoryEntityLinks = repositoryEntityLinks;
  }

  @PostMapping(path = "${tailormap-api.admin.base-path}/geo-services/{id}/refresh-capabilities")
  public ResponseEntity<GeoService> refreshCapabilities(
      @PathVariable String id, HttpServletResponse httpServletResponse) throws Exception {

    GeoService geoService =
        geoServiceRepository
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

    // Authorization check not needed: only admins are allowed on the admin base path, and admins
    // have all access

    geoService.setRefreshCapabilities(true);
    applicationContext.publishEvent(new BeforeSaveEvent(geoService));

    geoServiceRepository.saveAndFlush(geoService);

    httpServletResponse.sendRedirect(
        String.valueOf(repositoryEntityLinks.linkToItemResource(GeoService.class, id).toUri()));
    return null;
  }
}
