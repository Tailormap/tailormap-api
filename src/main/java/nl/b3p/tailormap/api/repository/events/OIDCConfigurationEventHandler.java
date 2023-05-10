/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.repository.events;

import nl.b3p.tailormap.api.persistence.OIDCConfiguration;
import nl.b3p.tailormap.api.security.OIDCRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.rest.core.annotation.HandleAfterCreate;
import org.springframework.data.rest.core.annotation.HandleAfterDelete;
import org.springframework.data.rest.core.annotation.HandleAfterSave;
import org.springframework.data.rest.core.annotation.HandleBeforeCreate;
import org.springframework.data.rest.core.annotation.HandleBeforeSave;
import org.springframework.data.rest.core.annotation.RepositoryEventHandler;
import org.springframework.stereotype.Component;

@RepositoryEventHandler
@Component
public class OIDCConfigurationEventHandler {
  @Autowired OIDCRepository oidcRepository;

  public OIDCConfigurationEventHandler() {}

  @HandleBeforeCreate
  @HandleBeforeSave
  public void handleBeforeCreateOrSave(OIDCConfiguration configuration) throws Exception {
    // If the user provided a "full" OIDC discovery URL, strip it; Spring does not handle it.
    if (configuration.getIssuerUrl().endsWith("/.well-known/openid-configuration")) {
      String issuerUrl = configuration.getIssuerUrl();
      // Keep the trailing slash we'd have. Not for any good reason. Just feels "right".
      issuerUrl =
          issuerUrl.substring(0, issuerUrl.lastIndexOf("/.well-known/openid-configuration") + 1);
      configuration.setIssuerUrl(issuerUrl);
    }
  }

  @HandleAfterCreate
  @HandleAfterSave
  @HandleAfterDelete
  public void handleAfterCreateOrSaveOrDelete(OIDCConfiguration configuration) throws Exception {
    oidcRepository.synchronize();
  }
}
