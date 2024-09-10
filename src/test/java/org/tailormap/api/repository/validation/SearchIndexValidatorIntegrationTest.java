/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.repository.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import org.tailormap.api.annotation.PostgresIntegrationTest;
import org.tailormap.api.persistence.SearchIndex;
import org.tailormap.api.repository.FeatureTypeRepository;

@PostgresIntegrationTest
class SearchIndexValidatorIntegrationTest {
  private SearchIndexValidator searchIndexValidator;

  @Autowired private FeatureTypeRepository featureTypeRepository;

  @BeforeEach
  void setUp() {
    searchIndexValidator = new SearchIndexValidator(featureTypeRepository);
  }

  @Test
  void supports() {
    assertTrue(searchIndexValidator.supports(SearchIndex.class));
    assertFalse(searchIndexValidator.supports(Object.class));
  }

  @Test
  void testWFSFeatureType() {
    SearchIndex wfsIndex = new SearchIndex().setFeatureTypeId(15L);
    Errors errors = new BeanPropertyBindingResult(wfsIndex, "");
    searchIndexValidator.validate(wfsIndex, errors);

    assertEquals(1, errors.getErrorCount(), "Expected 1 error");
    assertEquals(
        "This feature type is not available for indexing.",
        errors.getAllErrors().get(0).getDefaultMessage(),
        "Did not get expected error message");
  }

  @Test
  void testJDBCFeatureType() {
    SearchIndex wfsIndex = new SearchIndex().setFeatureTypeId(26L);
    Errors errors = new BeanPropertyBindingResult(wfsIndex, "");
    searchIndexValidator.validate(wfsIndex, errors);

    assertEquals(0, errors.getErrorCount(), "Expected no errors");
  }
}
