/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.repository.events;

import org.springframework.data.rest.core.annotation.HandleAfterCreate;
import org.springframework.data.rest.core.annotation.HandleAfterDelete;
import org.springframework.data.rest.core.annotation.HandleAfterSave;
import org.springframework.data.rest.core.annotation.HandleBeforeCreate;
import org.springframework.data.rest.core.annotation.HandleBeforeSave;
import org.springframework.data.rest.core.annotation.RepositoryEventHandler;
import org.springframework.stereotype.Component;
import org.tailormap.api.persistence.OIDCConfiguration;
import org.tailormap.api.security.OIDCRepository;

@RepositoryEventHandler
@Component
public class OIDCConfigurationEventHandler {
  private final OIDCRepository oidcRepository;

  public OIDCConfigurationEventHandler(OIDCRepository oidcRepository) {
    this.oidcRepository = oidcRepository;
  }

  @HandleBeforeCreate
  @HandleBeforeSave
  public void handleBeforeCreateOrSave(OIDCConfiguration configuration) {
    // If the user provided a "full" OIDC discovery URL, strip it.
    if (configuration.getIssuerUrl().endsWith("/.well-known/openid-configuration")) {
      String issuerUrl = configuration.getIssuerUrl();
      issuerUrl = issuerUrl.substring(0, issuerUrl.lastIndexOf("/.well-known/openid-configuration"));
      configuration.setIssuerUrl(issuerUrl);
    }
  }

  @HandleAfterCreate
  @HandleAfterSave
  @HandleAfterDelete
  public void handleAfterCreateOrSaveOrDelete(OIDCConfiguration configuration) {
    oidcRepository.synchronize();
  }
}
