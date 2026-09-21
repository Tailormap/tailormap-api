/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.controller.admin;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.rest.webmvc.support.RepositoryEntityLinks;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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

  /**
   * Create a new feature source and attach it to a catalog node.
   *
   * @param featureSource the feature source to create, must contain a catalogNodeId to attach to
   * @return the created feature source as a HATEOAS resource with a link to the resource
   * @throws IOException if there is an error loading capabilities
   * @throws ObjectOptimisticLockingFailureException if there is a concurrent modification likely of the catalog
   */
  @PostMapping(path = "${tailormap-api.admin.base-path}/feature-sources/new")
  @Transactional
  public ResponseEntity<EntityModel<TMFeatureSource>> createFeatureSource(
      @RequestBody @Valid TMFeatureSource featureSource)
      throws IOException, ObjectOptimisticLockingFailureException {
    final String catalogNodeId = featureSource.getCatalogNodeId();
    if (StringUtils.isBlank(catalogNodeId)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Catalog node id is required");
    }

    try {
      featureSource = tmFeatureSourceHelper.createFeatureSource(featureSource);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    Catalog catalog = catalogRepository
        .findByIdWithLock(Catalog.MAIN)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Main catalog not found"));
    CatalogNode attachTo = catalog.getNodes().stream()
        .filter(node -> node.getId().equals(catalogNodeId))
        .findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Catalog node not found"));
    featureSource = featureSourceRepository.save(featureSource);
    attachTo.addItemsItem(new TailormapObjectRef()
        .id(featureSource.getId().toString())
        .kind(TailormapObjectRef.KindEnum.FEATURE_SOURCE));

    catalogRepository.saveAndFlush(catalog);
    featureSource = featureSourceRepository.saveAndFlush(featureSource);

    Link selfLink = repositoryEntityLinks.linkToItemResource(TMFeatureSource.class, featureSource.getId());
    return ResponseEntity.created(selfLink.toUri()).body(EntityModel.of(featureSource, selfLink));
  }
}
