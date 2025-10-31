/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.geotools.featuresources;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.startsWithIgnoringCase;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.tailormap.api.annotation.PostgresIntegrationTest;
import org.tailormap.api.persistence.TMFeatureType;
import org.tailormap.api.repository.FeatureSourceRepository;
import org.tailormap.api.repository.FeatureTypeRepository;

@PostgresIntegrationTest
class JDBCDataStoreHelperIntegrationTest {
  private JDBCDataStoreHelper helper;
  private TMFeatureType featureType;

  @Autowired
  private FeatureSourceRepository featureSourceRepository;

  @Autowired
  private FeatureTypeRepository featureTypeRepository;

  @BeforeEach
  void setUp() {
    helper = new JDBCDataStoreHelper();
  }

  private static Stream<Arguments> titlesAndNamesForFeatureSourcesAndFeatureTypes() {
    return Stream.of(
        Arguments.of("PostGIS", "bord"), Arguments.of("MS SQL Server", "bord"), Arguments.of("Oracle", "BORD"));
  }

  @ParameterizedTest
  @MethodSource("titlesAndNamesForFeatureSourcesAndFeatureTypes")
  void testGetCreateAttachmentsForFeatureTypeStatements(String fsTitle, String ftName) {
    featureType = featureTypeRepository
        .getTMFeatureTypeByNameAndFeatureSource(
            ftName, featureSourceRepository.getByTitle(fsTitle).orElseThrow())
        .orElseThrow();

    try {
      String sql = helper.getCreateAttachmentsForFeatureTypeStatements(featureType);
      assertNotNull(sql);
      switch (fsTitle) {
        case "Oracle" ->
          assertThat(
              sql,
              startsWithIgnoringCase("CREATE TABLE IF NOT EXISTS GEODATA." + ftName + "_attachments (\n"
                  + ftName + "_pk "));
        case "PostGIS" ->
          assertThat(
              sql,
              startsWithIgnoringCase("CREATE TABLE IF NOT EXISTS public." + ftName + "_attachments (\n"
                  + ftName + "_pk "));
        case "MS SQL Server" ->
          assertThat(
              sql,
              startsWithIgnoringCase(
                  "CREATE TABLE dbo." + ftName + "_attachments (\n" + ftName + "_pk "));
      }

    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  @ParameterizedTest
  @MethodSource("titlesAndNamesForFeatureSourcesAndFeatureTypes")
  void testGetCreateAttachmentsIndexForFeatureTypeStatements(String fsTitle, String ftName) {
    featureType = featureTypeRepository
        .getTMFeatureTypeByNameAndFeatureSource(
            ftName, featureSourceRepository.getByTitle(fsTitle).orElseThrow())
        .orElseThrow();

    try {
      String actual = helper.getCreateAttachmentsIndexForFeatureTypeStatements(featureType);
      assertNotNull(actual);

      String expected =
          switch (fsTitle) {
            case "PostGIS" ->
              "CREATE INDEX IF NOT EXISTS " + ftName + "_attachments_fk ON public." + ftName
                  + "_attachments(" + ftName + "_pk)";
            case "Oracle" ->
              ("CREATE INDEX IF NOT EXISTS GEODATA." + ftName + "_attachments_fk ON GEODATA." + ftName
                      + "_attachments(" + ftName + "_pk)")
                  .toUpperCase(Locale.ROOT);
            case "MS SQL Server" ->
              "CREATE INDEX " + ftName + "_attachments_fk ON dbo." + ftName + "_attachments(" + ftName
                  + "_pk)";
            default -> null;
          };

      assertEquals(expected, actual);
    } catch (Exception e) {
      fail(e.getMessage());
    }
  }
}
