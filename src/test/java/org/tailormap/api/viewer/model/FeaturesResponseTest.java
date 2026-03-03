/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.viewer.model;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class FeaturesResponseTest {
  @Test
  void required_fields_must_not_be_null() {
    FeaturesResponse featuresResponse = new FeaturesResponse();
    assertNotNull(featuresResponse.getFeatures(), "featurelist should never be null");
    assertNotNull(featuresResponse.getColumnMetadata(), "columnMetadata should never be null");

    assertNull(featuresResponse.getPage(), "default page should be null");
    assertNull(featuresResponse.getPageSize(), "default pageSize should be null");
    assertNull(featuresResponse.getTotal(), "default total should be null");
  }
}
