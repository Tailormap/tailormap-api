/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package nl.b3p.tailormap.api.controller.admin;

import jakarta.servlet.http.HttpServletResponse;
import java.lang.invoke.MethodHandles;
import nl.b3p.tailormap.api.persistence.TMFeatureSource;
import nl.b3p.tailormap.api.repository.FeatureSourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.data.rest.core.event.BeforeSaveEvent;
import org.springframework.data.rest.webmvc.support.RepositoryEntityLinks;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class FeatureSourceAdminController {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private final FeatureSourceRepository featureSourceRepository;
  private final ApplicationContext applicationContext;
  private final RepositoryEntityLinks repositoryEntityLinks;

  public FeatureSourceAdminController(
      FeatureSourceRepository featureSourceRepository,
      ApplicationContext applicationContext,
      RepositoryEntityLinks repositoryEntityLinks) {
    this.featureSourceRepository = featureSourceRepository;
    this.applicationContext = applicationContext;
    this.repositoryEntityLinks = repositoryEntityLinks;
  }

  @PostMapping(path = "${tailormap-api.admin.base-path}/feature-sources/{id}/refresh-capabilities")
  @Transactional
  public ResponseEntity<?> refreshCapabilities(
      @PathVariable Long id, HttpServletResponse httpServletResponse) throws Exception {

    TMFeatureSource featureSource =
        featureSourceRepository
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

    // Authorization check not needed: only admins are allowed on the admin base path, and admins
    // have all access

    logger.info("Loading capabilities for feature source {}", featureSource);
    featureSource.setRefreshCapabilities(true);
    applicationContext.publishEvent(new BeforeSaveEvent(featureSource));

    httpServletResponse.sendRedirect(
        String.valueOf(
            repositoryEntityLinks.linkToItemResource(TMFeatureSource.class, id).toUri()));
    return null;
  }
}
