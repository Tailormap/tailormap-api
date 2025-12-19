/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.repository.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Objects;
import java.util.stream.Stream;
import org.geotools.jdbc.JDBCDataStore;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.tailormap.api.annotation.PostgresIntegrationTest;
import org.tailormap.api.geotools.featuresources.JDBCFeatureSourceHelper;
import org.tailormap.api.persistence.TMFeatureType;
import org.tailormap.api.persistence.json.AttachmentAttributeType;
import org.tailormap.api.repository.FeatureSourceRepository;
import org.tailormap.api.repository.FeatureTypeRepository;

@PostgresIntegrationTest
class FeatureTypeEventHandlerIntegrationTest {
  @Autowired
  private FeatureSourceRepository featureSourceRepository;

  @Autowired
  private FeatureTypeRepository featureTypeRepository;

  @Autowired
  private FeatureTypeEventHandler featureTypeEventHandler;

  private static Stream<Arguments> titlesAndNamesForFeatureSourcesAndFeatureTypes() {
    return Stream.of(
        Arguments.of("PostGIS", "bord"),
        Arguments.of("MS SQL Server", "bord"),
        Arguments.of("Oracle", "BORD"),
        Arguments.of("PostGIS OSM", "osm_polygon"));
  }

  @ParameterizedTest
  @MethodSource("titlesAndNamesForFeatureSourcesAndFeatureTypes")
  void feature_type_event_handler_creates_attachment_tables(String fsTitle, String ftName) throws Exception {
    TMFeatureType featureType = featureTypeRepository
        .getTMFeatureTypeByNameAndFeatureSource(
            ftName, featureSourceRepository.getByTitle(fsTitle).orElseThrow())
        .orElseThrow();

    featureType
        .getSettings()
        .addAttachmentAttributesItem(new AttachmentAttributeType()
            .attributeName("bord_photos")
            .maxAttachmentSize(4_000_000L)
            .mimeType("image/jpeg"));
    featureTypeEventHandler.handleBeforeSaveTMFeatureType(featureType);

    JDBCDataStore ds = null;
    try {
      ds = (JDBCDataStore) new JDBCFeatureSourceHelper().createDataStore(featureType.getFeatureSource());

      if (Objects.equals(ftName, "osm_polygon")) ftName = ds.getDatabaseSchema() + "." + ftName;
      try (Connection conn = ds.getDataSource().getConnection();
          Statement stmt = conn.createStatement();
          ResultSet rs = stmt.executeQuery("select count(*) from " + ftName + "_attachments")) {
        if (rs.next()) {
          int count = rs.getInt(1);
          assertEquals(0, count, "Attachments table exists but is not empty.");
        } else {
          fail("Attachments table does not exist.");
        }

        // cleanup
        stmt.execute("drop table " + ftName + "_attachments");
      }
    } finally {
      if (ds != null) {
        ds.dispose();
      }
    }
  }

  @ParameterizedTest
  @MethodSource("titlesAndNamesForFeatureSourcesAndFeatureTypes")
  void feature_type_event_handler_no_attachment_attributes(String fsTitle, String ftName) {
    TMFeatureType featureType = featureTypeRepository
        .getTMFeatureTypeByNameAndFeatureSource(
            ftName, featureSourceRepository.getByTitle(fsTitle).orElseThrow())
        .orElseThrow();

    try {
      featureTypeEventHandler.handleBeforeSaveTMFeatureType(featureType);
    } catch (Exception e) {
      fail("Exception thrown when no attachment attributes are present: " + e.getMessage());
    }
  }
}
