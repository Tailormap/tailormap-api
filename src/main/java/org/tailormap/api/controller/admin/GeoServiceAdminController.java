/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.controller.admin;

import jakarta.servlet.http.HttpServletResponse;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.data.rest.core.event.BeforeSaveEvent;
import org.springframework.data.rest.webmvc.support.RepositoryEntityLinks;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.tailormap.api.persistence.GeoService;
import org.tailormap.api.persistence.Group;
import org.tailormap.api.persistence.helper.AdminAdditionalPropertyHelper;
import org.tailormap.api.persistence.json.GeoServiceLayer;
import org.tailormap.api.repository.GeoServiceRepository;
import org.tailormap.api.security.TailormapUserDetails;

@RestController
public class GeoServiceAdminController {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

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
  public ResponseEntity<List<GeoServiceLayer>> refreshCapabilities(
      @PathVariable String id, HttpServletResponse httpServletResponse) throws Exception {

    GeoService geoService =
        geoServiceRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

    // Check authorization: admins have full access, users with REFRESH_CAPABILITIES role need
    // service ID in their additional properties
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    assert authentication != null;
    boolean isAdmin =
        authentication.getAuthorities().stream().anyMatch(a -> Objects.equals(a.getAuthority(), Group.ADMIN));
    boolean hasRefreshCapabilities = authentication.getAuthorities().stream()
        .anyMatch(a -> Objects.equals(a.getAuthority(), Group.REFRESH_CAPABILITIES));

    if (authentication.getPrincipal() instanceof TailormapUserDetails userDetails) {
      if (!isAdmin) {
        if (!hasRefreshCapabilities) {
          // Should not be allowed by securityMatchers in ApiSecurityConfiguration
          throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        final Set<String> allowedGeoServiceIds = userDetails.getAdditionalGroupProperties().stream()
            .filter(p -> Objects.equals(
                p.key(), AdminAdditionalPropertyHelper.KEY_REFRESH_CAPABILITIES_SERVICES))
            .findFirst()
            .map(p -> {
              Set<String> allowedIds = new HashSet<>();
              switch (p.value()) {
                case null -> {
                  return allowedIds;
                }
                case String stringValue ->
                  allowedIds.addAll(Arrays.stream(stringValue.split(","))
                      .map(String::trim)
                      .toList());
                case ArrayList<?> list ->
                  list.stream()
                      .filter(item -> item instanceof String)
                      .map(item -> (String) item)
                      .forEach(allowedIds::add);
                default -> {}
              }
              return allowedIds;
            })
            .orElse(new HashSet<>());

        if (!allowedGeoServiceIds.contains(geoService.getId())) {
          throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        logger.debug(
            "User {} is authorized to refresh capabilities for GeoService {} (id: {})",
            userDetails.getUsername(),
            geoService.getTitle(),
            geoService.getId());
      }
    }

    geoService.setRefreshCapabilities(true);
    applicationContext.publishEvent(new BeforeSaveEvent(geoService));

    geoServiceRepository.saveAndFlush(geoService);

    if (isAdmin) {
      httpServletResponse.sendRedirect(String.valueOf(repositoryEntityLinks
          .linkToItemResource(GeoService.class, id)
          .toUri()));
      return null;
    } else {
      // Do not redirect to the GeoService resource (which they don't have access to), but return the refreshed
      // layers directly (which do not contain sensitive information such as authentication credentials)
      return ResponseEntity.ok(geoService.getLayers());
    }
  }
}
