/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.repository.events;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.sql.SQLException;
import java.sql.Statement;
import org.geotools.jdbc.JDBCDataStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.rest.core.annotation.HandleBeforeSave;
import org.springframework.data.rest.core.annotation.RepositoryEventHandler;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.tailormap.api.geotools.featuresources.JDBCDataStoreHelper;
import org.tailormap.api.geotools.featuresources.JDBCFeatureSourceHelper;
import org.tailormap.api.persistence.TMFeatureType;

@Component
@RepositoryEventHandler
public class FeatureTypeEventHandler {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @HandleBeforeSave
  void handleBeforeSaveTMFeatureType(TMFeatureType featureType) {
    try {
      if (featureType.getSettings().getAttachmentAttributes() != null
          && !featureType.getSettings().getAttachmentAttributes().isEmpty()) {
        createAttachmentTableForFeatureType(featureType);
      }
    } catch (IllegalArgumentException | SQLException | IOException e) {
      logger.error(
          "Error opening GeoTools datastore creating attachments table or index for FeatureType: {}",
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

  /**
   * Create attachment table and index for the given FeatureType.
   *
   * @param featureType the FeatureType to create the attachment table for
   * @throws IOException when creating the GeoTools datastore fails
   * @throws SQLException when executing the SQL statements fails
   * @throws IllegalArgumentException when the FeatureType is invalid
   */
  private void createAttachmentTableForFeatureType(TMFeatureType featureType)
      throws IOException, SQLException, IllegalArgumentException {
    logger.debug(
        "Creating attachment table for FeatureType: {} and attachment names {}",
        featureType.getName(),
        featureType.getSettings().getAttachmentAttributes());

    JDBCDataStoreHelper helper = new JDBCDataStoreHelper();
    JDBCDataStore ds = null;
    try {
      ds = (JDBCDataStore) new JDBCFeatureSourceHelper().createDataStore(featureType.getFeatureSource());

      try (Statement stmt = ds.getDataSource().getConnection().createStatement()) {

        String sql = helper.getCreateAttachmentsForFeatureTypeStatements(featureType);
        logger.debug("About to create attachments table using {}", sql);

        stmt.execute(sql);
        logger.info("Attachment table created for FeatureType: {}", featureType.getName());
        sql = helper.getCreateAttachmentsIndexForFeatureTypeStatements(featureType);
        logger.debug("About to create attachments table FK index using {}", sql);
        stmt.execute(sql);
        logger.info("Attachment table FK index created for FeatureType: {}", featureType.getName());
      }
    } finally {
      if (ds != null) {
        ds.dispose();
      }
    }
  }
}
