/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.controller.admin;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.rest.webmvc.support.RepositoryEntityLinks;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.tailormap.api.persistence.Catalog;
import org.tailormap.api.persistence.TMFeatureSource;
import org.tailormap.api.persistence.helper.TMFeatureSourceHelper;
import org.tailormap.api.persistence.json.CatalogNode;
import org.tailormap.api.persistence.json.TailormapObjectRef;
import org.tailormap.api.repository.CatalogRepository;
import org.tailormap.api.repository.FeatureSourceRepository;
import org.tailormap.api.viewer.model.ErrorResponse;

@RestController
public class FeatureSourceAdminController {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private final FeatureSourceRepository featureSourceRepository;
  private final TMFeatureSourceHelper tmFeatureSourceHelper;
  private final CatalogRepository catalogRepository;
  private final RepositoryEntityLinks repositoryEntityLinks;

  public FeatureSourceAdminController(
      FeatureSourceRepository featureSourceRepository,
      TMFeatureSourceHelper tmFeatureSourceHelper,
      CatalogRepository catalogRepository,
      RepositoryEntityLinks repositoryEntityLinks) {
    this.featureSourceRepository = featureSourceRepository;
    this.tmFeatureSourceHelper = tmFeatureSourceHelper;
    this.catalogRepository = catalogRepository;
    this.repositoryEntityLinks = repositoryEntityLinks;
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

  @ExceptionHandler({IOException.class})
  public ResponseEntity<?> internalServerError(Exception ex) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .contentType(MediaType.APPLICATION_JSON)
        .body(new ErrorResponse().message(ex.getMessage()).code(HttpStatus.INTERNAL_SERVER_ERROR.value()));
  }

  @PostMapping(path = "${tailormap-api.admin.base-path}/feature-sources/{id}/refresh-capabilities")
  @Transactional
  public ResponseEntity<?> refreshCapabilities(@PathVariable Long id, HttpServletResponse httpServletResponse)
      throws IOException {

    TMFeatureSource featureSource = featureSourceRepository
        .findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

    logger.info("Loading capabilities for feature source {}", featureSource);

    tmFeatureSourceHelper.loadCapabilities(featureSource);
    featureSourceRepository.saveAndFlush(featureSource);

    httpServletResponse.sendRedirect(String.valueOf(repositoryEntityLinks
        .linkToItemResource(TMFeatureSource.class, id)
        .toUri()));
    return null;
  }

  @PostMapping(path = "${tailormap-api.admin.base-path}/feature-sources/new")
  public ResponseEntity<?> createFeatureSource(@RequestBody TMFeatureSource featureSource) throws IOException {
    final String catalogNodeId = featureSource.getCatalogNodeId();
    if (StringUtils.isBlank(catalogNodeId)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Catalog node id is required");
    }
    Catalog catalog = catalogRepository.findById(Catalog.MAIN).orElseThrow();
    CatalogNode attachTo = catalog.getNodes().stream()
        .filter(node -> node.getId().equals(catalogNodeId))
        .findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Catalog node not found"));

    try {
      featureSource = tmFeatureSourceHelper.createFeatureSource(featureSource);
      featureSource = featureSourceRepository.save(featureSource);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    attachTo.addItemsItem(new TailormapObjectRef()
        .id(featureSource.getId().toString())
        .kind(TailormapObjectRef.KindEnum.FEATURE_SOURCE));

    catalogRepository.saveAndFlush(catalog);
    featureSource = featureSourceRepository.saveAndFlush(featureSource);

    return ResponseEntity.created(repositoryEntityLinks
            .linkToItemResource(TMFeatureSource.class, featureSource.getId())
            .toUri())
        .build();
  }
}
