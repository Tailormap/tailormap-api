/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.repository.events;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.rest.core.annotation.HandleBeforeSave;
import org.springframework.data.rest.core.annotation.RepositoryEventHandler;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.tailormap.api.geotools.featuresources.AttachmentsHelper;
import org.tailormap.api.persistence.TMFeatureType;

@Component
@RepositoryEventHandler
public class FeatureTypeEventHandler {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @HandleBeforeSave
  void handleBeforeSaveTMFeatureType(TMFeatureType featureType) {
    try {
      if (featureType.getSettings() != null
          && featureType.getSettings().getAttachmentAttributes() != null
          && !featureType.getSettings().getAttachmentAttributes().isEmpty()) {
        AttachmentsHelper.createAttachmentTableForFeatureType(featureType);
      }
    } catch (IllegalArgumentException | SQLException | IOException e) {
      logger.error(
          "Error opening GeoTools datastore or creating attachments table or index for FeatureType: {}",
          featureType.getName(),
          e);
      // TODO check if this is the correct exception to throw,
      //  we may want to throw eg a BadRequest or illegal state/illegal argument exception instead
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "Error creating attachments table or index for FeatureType: " + featureType.getName(),
          e);
    }
  }
}
