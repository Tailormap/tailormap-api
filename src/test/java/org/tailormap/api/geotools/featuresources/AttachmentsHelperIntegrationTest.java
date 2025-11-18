/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.geotools.featuresources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.geotools.api.data.Query;
import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.feature.FeatureIterator;
import org.geotools.jdbc.JDBCDataStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.tailormap.api.configuration.JPAConfiguration;
import org.tailormap.api.persistence.TMFeatureType;
import org.tailormap.api.persistence.json.AttachmentAttributeType;
import org.tailormap.api.repository.FeatureSourceRepository;
import org.tailormap.api.repository.FeatureTypeRepository;
import org.tailormap.api.viewer.model.AttachmentMetadata;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SpringBootTest(
    classes = {
      JPAConfiguration.class,
      DataSourceAutoConfiguration.class,
      HibernateJpaAutoConfiguration.class,
    })
@ComponentScan(basePackages = {"org.tailormap.api"})
@ActiveProfiles("postgresql")
@ParameterizedClass
@DisplayName("AttachmentsHelper integration test")
@MethodSource("titlesAndNamesForFeatureSourcesAndFeatureTypes")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ContextConfiguration
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
  private UUID attachmentAttributePKvalue;
  private AttachmentMetadata attachmentMetadata;
  private byte[] attachmentData = null;
  private Object featurePrimaryKey;
  private String schemaPrefix = "";

  static Stream<Arguments> titlesAndNamesForFeatureSourcesAndFeatureTypes() {
    return Stream.of(
        // these are in the default schema's
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
        // these are in the PostGIS OSM schema
        arguments("PostGIS OSM", "osm_polygon"));
  }

  @BeforeEach
  void setUp() throws IOException {
    featureType = featureTypeRepository
        .getTMFeatureTypeByNameAndFeatureSource(
            featureTypeName,
            featureSourceRepository
                .getByTitle(featureSourceTitle)
                .orElseThrow(() -> new IllegalArgumentException(
                    "Feature source not found: " + featureSourceTitle)))
        .orElseThrow(() -> new IllegalArgumentException(
            "Feature type not found: " + featureTypeName + " for feature source: " + featureSourceTitle));

    AttachmentAttributeType attachmentAttributeType = new AttachmentAttributeType()
        .attributeName(featureTypeName + "_photos")
        .maxAttachmentSize(4_000_000L)
        .mimeType("image/jpeg, image/svg+xml, image/png");

    attachmentData = new ClassPathResource("test/lichtpunt.svg").getContentAsByteArray();
    attachmentMetadata = new AttachmentMetadata()
        .attributeName(attachmentAttributeType.getAttributeName())
        .description("Test attachment: lichtpunt")
        .fileName("lichtpunt.svg")
        .mimeType("image/svg+xml")
        .attachmentSize((long) attachmentData.length);

    featureType.getSettings().addAttachmentAttributesItem(attachmentAttributeType);

    ds = (JDBCDataStore) new JDBCFeatureSourceHelper().createDataStore(featureType.getFeatureSource());

    schemaPrefix = "osm_polygon".equalsIgnoreCase(featureTypeName) ? ds.getDatabaseSchema() + "." : "";
  }

  @AfterEach
  void tearDown() throws SQLException {
    if (ds != null) {
      ds.dispose();
    }
  }

  @Order(1)
  @Test
  @DisplayName("Create attachments table for feature type.")
  void createAttachmentsTableForFeatureType() {
    try {
      AttachmentsHelper.createAttachmentTableForFeatureType(featureType);

      try (Connection conn = ds.getDataSource().getConnection();
          Statement stmt = conn.createStatement();
          ResultSet rs = stmt.executeQuery(
              "select count(*) from " + schemaPrefix + featureTypeName + "_attachments")) {
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

  @Order(2)
  @Test
  @DisplayName("Add an attachment to the first feature of a type.")
  @WithMockUser(username = "unittestuser")
  void addAttachmentToFirstFeatureOfFeatureType() {
    try {
      SimpleFeatureSource fs = ds.getFeatureSource(featureTypeName);
      Query query = new Query(featureTypeName);
      query.setMaxFeatures(1);
      query.setPropertyNames(featureType.getPrimaryKeyAttribute());

      try (FeatureIterator<SimpleFeature> featureIterator =
          fs.getFeatures(query).features()) {
        if (featureIterator.hasNext()) {
          featurePrimaryKey = featureIterator.next().getAttribute(featureType.getPrimaryKeyAttribute());
        } else {
          fail("No feature found in feature type table to get a valid UUID primary key value.");
        }
      }
    } catch (IOException e) {
      fail("Failed to retrieve UUID primary key value: " + e.getMessage());
    }

    try {
      AttachmentMetadata inserted = AttachmentsHelper.insertAttachment(
          featureType, attachmentMetadata, featurePrimaryKey, attachmentData);

      assertNotNull(inserted);
      assertNotNull(inserted.getAttachmentId());
      attachmentAttributePKvalue = inserted.getAttachmentId();

      try (Connection conn = ds.getDataSource().getConnection();
          Statement stmt = conn.createStatement();
          ResultSet rs = stmt.executeQuery(
              "select count(*) from " + schemaPrefix + featureTypeName + "_attachments")) {
        if (rs.next()) {
          int count = rs.getInt(1);
          assertEquals(1, count, "Attachment was not inserted correctly.");
        } else {
          fail("Attachments table '%s_attachments' does not exist.".formatted(featureTypeName));
        }
      }
    } catch (SQLException | IOException e) {
      fail(e.getMessage());
    }
  }

  @Order(3)
  @Test
  @DisplayName("List all attachments for a single feature of a type.")
  void listAttachmentsForFeatureType() {
    try {
      List<AttachmentMetadata> attachments =
          AttachmentsHelper.listAttachmentsForFeature(featureType, featurePrimaryKey);
      assertNotNull(attachments);
      assertEquals(1, attachments.size(), "Expected exactly one attachment.");
      AttachmentMetadata listed = attachments.getFirst();
      assertEquals(attachmentMetadata.getFileName(), listed.getFileName());
      assertEquals(attachmentMetadata.getMimeType(), listed.getMimeType());
      assertEquals(attachmentMetadata.getAttachmentSize(), listed.getAttachmentSize());
    } catch (SQLException | RuntimeException | IOException e) {
      fail(e.getMessage());
    }
  }

  @Order(3)
  @Test
  @DisplayName("Retrieve binary attachment from feature type.")
  void retrieveBinaryAttachmentFromFeatureType() {
    try {
      AttachmentsHelper.AttachmentWithBinary retrieved =
          AttachmentsHelper.getAttachment(featureType, attachmentAttributePKvalue);
      assertNotNull(retrieved);
      assertNotNull(retrieved.attachmentMetadata());
      assertNotNull(retrieved.attachment());
      assertEquals(
          attachmentMetadata.getFileName(),
          retrieved.attachmentMetadata().getFileName());
      assertEquals(
          attachmentMetadata.getMimeType(),
          retrieved.attachmentMetadata().getMimeType());
      assertEquals(
          attachmentMetadata.getAttachmentSize(),
          retrieved.attachmentMetadata().getAttachmentSize());
      assertEquals(
          ByteBuffer.wrap(attachmentData),
          retrieved.attachment(),
          "Attachment binary data does not match inserted data.");
    } catch (SQLException | IOException e) {
      fail(e.getMessage());
    }
  }

  @Order(4)
  @Test
  @DisplayName("Get attachments for a list of features of a type.")
  void testListAttachmentsForFeaturesByFeatureId() {
    try {
      assertNotNull(featurePrimaryKey);
      Comparable<?> featurePrimarySafeKey = AttachmentsHelper.checkAndMakeFeaturePkComparable(featurePrimaryKey);
      Map<@NotNull Comparable<?>, List<AttachmentMetadata>> listAttachments =
          AttachmentsHelper.listAttachmentsForFeaturesByFeatureId(
              featureType, List.of(featurePrimarySafeKey));
      assertNotNull(listAttachments);
      assertEquals(1, listAttachments.size(), "Expected exactly one feature.");
      assertNotNull(listAttachments.get(featurePrimarySafeKey));
      assertEquals(1, listAttachments.get(featurePrimarySafeKey).size(), "Expected exactly one attachment.");
    } catch (RuntimeException | IOException e) {
      fail(e);
    }
  }

  /**
   * We would do this in @AfterAll, but because we're in a parameterized test class that fails so we need to do it in
   * a regular test with the highest order.
   *
   * @throws SQLException when SQL error occurs
   */
  @Order(Integer.MAX_VALUE)
  @Test
  @DisplayName("Delete attachments table for feature type.")
  void deleteAttachmentsTableForFeatureType() throws SQLException, IOException {
    try {
      AttachmentsHelper.dropAttachmentTableForFeatureType(featureType);

      try (Connection conn = ds.getDataSource().getConnection();
          Statement stmt = conn.createStatement();
          ResultSet rs = stmt.executeQuery(
              "select count(*) from " + schemaPrefix + featureTypeName + "_attachments")) {
        if (rs.next()) {
          fail("Attachments table '%s_attachments' still exists.".formatted(featureTypeName));
        }
      } catch (SQLException e) {
        // expected, table should not exist
      }

    } catch (SQLException | IOException e) {
      fail(e.getMessage());
    }
  }
}
