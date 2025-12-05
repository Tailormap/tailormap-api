/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.persistence.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.tailormap.api.annotation.PostgresIntegrationTest;
import org.tailormap.api.persistence.Application;
import org.tailormap.api.persistence.TMFeatureType;
import org.tailormap.api.persistence.json.AppTreeLayerNode;
import org.tailormap.api.repository.ApplicationRepository;
import org.tailormap.api.repository.FeatureSourceRepository;
import org.tailormap.api.repository.FeatureTypeRepository;

@PostgresIntegrationTest
class TMFeatureTypeHelperIntegrationTest {

  private static Application application;

  @Autowired
  private FeatureTypeRepository featureTypeRepository;

  @Autowired
  private FeatureSourceRepository featureSourceRepository;

  @BeforeAll
  static void beforeAll(@Autowired ApplicationRepository applicationRepository) {
    application = applicationRepository.findByName("default");
  }

  static Stream<Arguments> is_editable() {
    return Stream.of(
        arguments("PostGIS", "begroeidterreindeel", "postgis:begroeidterreindeel", true),
        arguments("PostGIS OSM", "osm_polygon", "postgis:osm_polygon", false));
  }

  @ParameterizedTest
  @MethodSource
  void is_editable(String featureSourceTitle, String featureTypeName, String layerName, boolean expectedEditable) {
    TMFeatureType featureType = featureTypeRepository
        .getTMFeatureTypeByNameAndFeatureSource(
            featureTypeName,
            featureSourceRepository
                .getByTitle(featureSourceTitle)
                .orElseThrow(() -> new IllegalArgumentException(
                    "Feature source not found: " + featureSourceTitle)))
        .orElseThrow(() -> new IllegalArgumentException(
            "Feature type not found: " + featureTypeName + " for feature source: " + featureSourceTitle));

    AppTreeLayerNode appTreeLayerNode = application
        .getAllAppTreeLayerNode()
        .filter(node -> node.getLayerName().equals(layerName))
        .findFirst()
        .orElseThrow();

    boolean actualEditable = TMFeatureTypeHelper.isEditable(application, appTreeLayerNode, featureType);
    assertEquals(
        expectedEditable,
        actualEditable,
        "Expected and actual editable status are not equal for feature type: " + featureTypeName);
  }
}
