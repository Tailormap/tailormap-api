/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.geotools.featuresources;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Objects;
import java.util.stream.Stream;
import org.geotools.jdbc.JDBCDataStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.springframework.beans.factory.annotation.Autowired;
import org.tailormap.api.annotation.PostgresIntegrationTest;
import org.tailormap.api.persistence.TMFeatureType;
import org.tailormap.api.persistence.json.AttachmentAttributeType;
import org.tailormap.api.repository.FeatureSourceRepository;
import org.tailormap.api.repository.FeatureTypeRepository;

@PostgresIntegrationTest
class AttachmentsHelperIntegrationTest {
  @Autowired
  private FeatureSourceRepository featureSourceRepository;

  @Autowired
  private FeatureTypeRepository featureTypeRepository;

  private static Stream<Arguments> titlesAndNamesForFeatureSourcesAndFeatureTypes() {
    return Stream.of(
        Arguments.of("PostGIS", "bord"),
        Arguments.of("MS SQL Server", "bord"),
        Arguments.of("Oracle", "BORD"),
        Arguments.of("PostGIS OSM", "osm_polygon"));
  }

  @ParameterizedTest
  @MethodSource("titlesAndNamesForFeatureSourcesAndFeatureTypes")
  void testGetCreateAttachmentsForFeatureTypeStatements(String fsTitle, String ftName) {
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

    JDBCDataStore ds = null;
    try {
      AttachmentsHelper.createAttachmentTableForFeatureType(featureType);
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
        // cleanup created attachments table
        stmt.execute("drop table " + ftName + "_attachments");
      }
    } catch (Exception e) {
      fail(e.getMessage());
    } finally {
      if (ds != null) {
        ds.dispose();
      }
    }
  }

  @ParameterizedTest
  @NullAndEmptySource
  void testInvalidAttachmentAttributes(String invalidInput) throws Exception {
    TMFeatureType featureType = featureTypeRepository
        .getTMFeatureTypeByNameAndFeatureSource(
            "bak", featureSourceRepository.getByTitle("PostGIS").orElseThrow())
        .orElseThrow();

    featureType
        .getSettings()
        .addAttachmentAttributesItem(new AttachmentAttributeType()
            .attributeName(invalidInput) // Invalid attribute name
            .maxAttachmentSize(4_000_000L)
            .mimeType("image/jpeg"));

    Exception exception = assertThrows(IllegalArgumentException.class, () -> {
      AttachmentsHelper.createAttachmentTableForFeatureType(featureType);
    });

    assertThat(
        exception.getMessage(),
        containsStringIgnoringCase(
            "FeatureType bak has an attachment attribute with invalid (null or empty) attribute name"));
  }

  @Test
  void testNullFeatureType() {
    Exception exception = assertThrows(IllegalArgumentException.class, () -> {
      AttachmentsHelper.createAttachmentTableForFeatureType(null);
    });

    assertThat(
        exception.getMessage(),
        containsStringIgnoringCase(
            "FeatureType null is invalid or has no attachment attributes defined in its settings"));
  }
}
