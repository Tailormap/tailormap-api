/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.geotools.featuresources;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.springframework.beans.factory.annotation.Autowired;
import org.tailormap.api.annotation.PostgresIntegrationTest;
import org.tailormap.api.persistence.TMFeatureType;
import org.tailormap.api.persistence.json.AttachmentAttributeType;
import org.tailormap.api.repository.FeatureSourceRepository;
import org.tailormap.api.repository.FeatureTypeRepository;

@PostgresIntegrationTest
class InvalidInputAttachmentsHelperIntegrationTest {
  @Autowired
  private FeatureSourceRepository featureSourceRepository;

  @Autowired
  private FeatureTypeRepository featureTypeRepository;

  @ParameterizedTest
  @NullAndEmptySource
  void invalid_attachment_attributes(String invalidInput) throws Exception {
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
  void create_attachment_table_for_null_feature_type() {
    Exception exception = assertThrows(IllegalArgumentException.class, () -> {
      AttachmentsHelper.createAttachmentTableForFeatureType(null);
    });

    assertThat(
        exception.getMessage(),
        containsStringIgnoringCase(
            "FeatureType null is invalid or has no attachment attributes defined in its settings"));
  }
}
