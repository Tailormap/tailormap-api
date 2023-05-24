/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package nl.b3p.tailormap.api.controller.admin;

import java.lang.invoke.MethodHandles;
import nl.b3p.tailormap.api.persistence.GeoService;
import nl.b3p.tailormap.api.persistence.helper.GeoServiceHelper;
import nl.b3p.tailormap.api.repository.GeoServiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class GeoServiceAdminController {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private final GeoServiceRepository geoServiceRepository;
  private final GeoServiceHelper geoServiceHelper;

  public GeoServiceAdminController(
      GeoServiceRepository geoServiceRepository, GeoServiceHelper geoServiceHelper) {
    this.geoServiceRepository = geoServiceRepository;
    this.geoServiceHelper = geoServiceHelper;
  }

  @PostMapping(path = "${tailormap-api.admin.base-path}/geo-services/{id}/refresh-capabilities")
  public ResponseEntity<GeoService> updateCapabilities(@PathVariable String id) throws Exception {

    GeoService geoService = geoServiceRepository.findById(id).orElse(null);

    if (geoService == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    // Authorization check not needed: only admins are allowed on the admin base path, and admins
    // have all access

    logger.info(
        "Loading capabilities for geo service \"{}\" from URL: \"{}\"",
        geoService.getId(),
        geoService.getUrl());
    geoServiceHelper.loadServiceCapabilities(geoService);

    return ResponseEntity.ok(geoService);
  }
}
