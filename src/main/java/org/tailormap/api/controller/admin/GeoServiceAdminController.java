/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.controller.admin;

import static org.tailormap.api.persistence.json.GeoServiceProtocol.XYZ;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.geotools.ows.ServiceException;
import org.springframework.data.rest.webmvc.support.RepositoryEntityLinks;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.tailormap.api.persistence.Catalog;
import org.tailormap.api.persistence.GeoService;
import org.tailormap.api.persistence.Group;
import org.tailormap.api.persistence.helper.GeoServiceHelper;
import org.tailormap.api.persistence.json.CatalogNode;
import org.tailormap.api.persistence.json.GeoServiceLayer;
import org.tailormap.api.persistence.json.TailormapObjectRef;
import org.tailormap.api.repository.CatalogRepository;
import org.tailormap.api.repository.GeoServiceRepository;
import org.tailormap.api.viewer.model.ErrorResponse;

@RestController
public class GeoServiceAdminController {

  private final GeoServiceRepository geoServiceRepository;
  private final RepositoryEntityLinks repositoryEntityLinks;
  private final GeoServiceHelper geoServiceHelper;
  private final CatalogRepository catalogRepository;

  public GeoServiceAdminController(
      GeoServiceRepository geoServiceRepository,
      RepositoryEntityLinks repositoryEntityLinks,
      GeoServiceHelper geoServiceHelper,
      CatalogRepository catalogRepository) {
    this.geoServiceRepository = geoServiceRepository;
    this.repositoryEntityLinks = repositoryEntityLinks;
    this.geoServiceHelper = geoServiceHelper;
    this.catalogRepository = catalogRepository;
  }

  @ExceptionHandler({
    IllegalArgumentException.class,
    UnsupportedOperationException.class,
    URISyntaxException.class,
    UnknownHostException.class
  })
  public ResponseEntity<?> badRequestException(Exception ex) {
    HttpStatus status = HttpStatus.BAD_REQUEST;
    String msg = ex.getMessage();
    //      case "IllegalArgumentException":
    //      case "UnsupportedOperationException":

    msg = switch (ex.getClass().getSimpleName()) {
      case "UnknownHostException" -> "Unknown host: \"" + ex.getMessage() + "\"";
      default -> msg;
    };

    return ResponseEntity.status(status)
        .contentType(MediaType.APPLICATION_JSON)
        .body(new ErrorResponse().message(msg).code(status.value()));
  }

  @ExceptionHandler({ServiceException.class, IOException.class})
  public ResponseEntity<?> internalServerError(Exception ex) {
    HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;

    return ResponseEntity.status(status)
        .contentType(MediaType.APPLICATION_JSON)
        .body(new ErrorResponse().message(ex.getMessage()).code(status.value()));
  }

  @ExceptionHandler({ResponseStatusException.class})
  public ResponseEntity<?> handleException(ResponseStatusException ex) {
    return ResponseEntity.status(ex.getStatusCode())
        .contentType(MediaType.APPLICATION_JSON)
        .body(new ErrorResponse()
            .message(
                ex.getReason() != null
                    ? ex.getReason()
                    : ex.getBody().getTitle())
            .code(ex.getStatusCode().value()));
  }

  @PostMapping(path = "${tailormap-api.admin.base-path}/geo-services/{id}/refresh-capabilities")
  @ResponseStatus
  public ResponseEntity<List<GeoServiceLayer>> refreshCapabilities(
      @PathVariable String id, HttpServletResponse httpServletResponse)
      throws IOException, IllegalArgumentException, URISyntaxException, ServiceException,
          UnsupportedOperationException {

    GeoService geoService =
        geoServiceRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    assert authentication != null;
    boolean isAdmin =
        authentication.getAuthorities().stream().anyMatch(a -> Objects.equals(a.getAuthority(), Group.ADMIN));
    boolean hasRefreshCapabilities = authentication.getAuthorities().stream()
        .anyMatch(a -> Objects.equals(a.getAuthority(), Group.REFRESH_CAPABILITIES));

    if (!isAdmin && !hasRefreshCapabilities) {
      // Should not be allowed by securityMatchers in ApiSecurityConfiguration
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    geoService = geoServiceHelper.loadServiceCapabilities(geoService);
    geoService = geoServiceRepository.saveAndFlush(geoService);

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

  @PostMapping(path = "${tailormap-api.admin.base-path}/geo-services/new")
  public ResponseEntity<List<GeoServiceLayer>> createGeoService(@RequestBody GeoService geoService)
      throws ServiceException, URISyntaxException, IOException {

    final String catalogNodeId = geoService.getCatalogNodeId();
    if (StringUtils.isBlank(catalogNodeId)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Catalog node id is required");
    }
    Catalog catalog = catalogRepository.findById(Catalog.MAIN).orElseThrow();
    CatalogNode attachTo = catalog.getNodes().stream()
        .filter(node -> node.getId().equals(catalogNodeId))
        .findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Catalog node not found"));

    URI uri;
    try {

      if (geoService.getProtocol() == XYZ) {
        // For XYZ URL templates, remove replacements
        // Besides {x}, {y}, {z} also allow {-y} (for TMS) and {a-c} for domains
        uri = new URI(geoService.getUrl().replaceAll("\\{[a-z\\-]+}", ""));
      } else {
        uri = new URI(geoService.getUrl());
      }
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid URI");
    }
    if (!"https".equals(uri.getScheme()) && !"http".equals(uri.getScheme())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid URI scheme");
    }

    geoService = geoServiceHelper.loadServiceCapabilities(geoService);
    geoService = geoServiceRepository.save(geoService);

    attachTo.addItemsItem(
        new TailormapObjectRef().id(geoService.getId()).kind(TailormapObjectRef.KindEnum.GEO_SERVICE));

    catalogRepository.saveAndFlush(catalog);
    geoService = geoServiceRepository.saveAndFlush(geoService);

    return ResponseEntity.created(repositoryEntityLinks
            .linkToItemResource(GeoService.class, geoService.getId())
            .toUri())
        .build();
  }
}
