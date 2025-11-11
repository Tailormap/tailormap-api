/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.geotools.featuresources;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.stream.Stream;
import org.geotools.jdbc.JDBCDataStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.tailormap.api.annotation.PostgresIntegrationTest;
import org.tailormap.api.persistence.TMFeatureType;
import org.tailormap.api.persistence.json.AttachmentAttributeType;
import org.tailormap.api.repository.FeatureSourceRepository;
import org.tailormap.api.repository.FeatureTypeRepository;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@PostgresIntegrationTest
@ParameterizedClass
@DisplayName("AttachmentsHelper integration test")
@MethodSource("titlesAndNamesForFeatureSourcesAndFeatureTypes")
class AttachmentsHelperIntegrationTest {

  @Autowired
  private FeatureSourceRepository featureSourceRepository;

  @Autowired
  private FeatureTypeRepository featureTypeRepository;

  @Parameter(0)
  private String featureSourceTitle;

  @Parameter(1)
  private String featureTypeName;

  private TMFeatureType featureType;
  private JDBCDataStore ds;

  static Stream<Arguments> titlesAndNamesForFeatureSourcesAndFeatureTypes() {
    return Stream.of(
        arguments("PostGIS", "bord"),
        arguments("PostGIS", "pk_variation_bigint"),
        arguments("PostGIS", "pk_variation_decimal"),
        arguments("PostGIS", "pk_variation_integer"),
        arguments("PostGIS", "pk_variation_numeric"),
        arguments("PostGIS", "pk_variation_serial"),
        arguments("PostGIS", "pk_variation_uuid"),
        arguments("MS SQL Server", "bord"),
        arguments("MS SQL Server", "pk_variation_bigint"),
        arguments("MS SQL Server", "pk_variation_decimal"),
        arguments("MS SQL Server", "pk_variation_integer"),
        arguments("MS SQL Server", "pk_variation_numeric"),
        arguments("MS SQL Server", "pk_variation_serial"),
        arguments("MS SQL Server", "pk_variation_uuid"),
        arguments("Oracle", "BORD"),
        arguments("Oracle", "PK_VARIATION_BIGINT"),
        arguments("Oracle", "PK_VARIATION_DECIMAL"),
        arguments("Oracle", "PK_VARIATION_INTEGER"),
        arguments("Oracle", "PK_VARIATION_NUMERIC"),
        arguments("Oracle", "PK_VARIATION_SERIAL"),
        arguments("Oracle", "PK_VARIATION_UUID"),
        arguments("PostGIS OSM", "osm_polygon"));
  }

  @BeforeEach
  void setUp() throws IOException {
    featureType = featureTypeRepository
        .getTMFeatureTypeByNameAndFeatureSource(
            featureTypeName,
            featureSourceRepository.getByTitle(featureSourceTitle).orElseThrow())
        .orElseThrow();

    featureType
        .getSettings()
        .addAttachmentAttributesItem(new AttachmentAttributeType()
            .attributeName(featureTypeName + "_photos")
            .maxAttachmentSize(4_000_000L)
            .mimeType("image/jpeg"));

    ds = (JDBCDataStore) new JDBCFeatureSourceHelper().createDataStore(featureType.getFeatureSource());
    if (Objects.equals(featureTypeName, "osm_polygon"))
      featureTypeName = ds.getDatabaseSchema() + "." + featureTypeName;
  }

  @AfterEach
  void tearDown() throws SQLException {
    featureType.getSettings().getAttachmentAttributes().clear();
    try (Connection conn = ds.getDataSource().getConnection();
        Statement stmt = conn.createStatement()) {
      boolean success = stmt.execute("drop table " + featureTypeName + "_attachments");
      assumeFalse(success, "Maybe failed to cleanup attachments table, we did not get an update count result.");
      assertThat(stmt.getUpdateCount(), is(lessThanOrEqualTo(1)));
    } finally {
      if (ds != null) {
        ds.dispose();
      }
    }
  }

  @Order(1)
  @Test
  @DisplayName("Create attachments table for feature type.")
  void createAttachmentsTableForFeatureTypeStatements() {
    try {
      AttachmentsHelper.createAttachmentTableForFeatureType(featureType);

      try (Connection conn = ds.getDataSource().getConnection();
          Statement stmt = conn.createStatement();
          ResultSet rs = stmt.executeQuery("select count(*) from " + featureTypeName + "_attachments")) {
        if (rs.next()) {
          int count = rs.getInt(1);
          assertEquals(0, count, "Attachments table exists but is not empty.");
        } else {
          fail("Attachments table '%s_attachments' does not exist.".formatted(featureTypeName));
        }
      }
    } catch (Exception e) {
      fail(e.getMessage());
    }
  }
}
