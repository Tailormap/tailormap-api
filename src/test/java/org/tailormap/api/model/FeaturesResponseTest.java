/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.model;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.tailormap.api.viewer.model.FeaturesResponse;

class FeaturesResponseTest {
  @Test
  void testRequiredFieldsNotNull() {
    FeaturesResponse fr = new FeaturesResponse();
    assertNotNull(fr.getFeatures(), "featurelist should not be null");
    assertNotNull(fr.getColumnMetadata(), "columnMetadata should not be null");
    assertNull(fr.getPage(), "default page should be null");
    assertNull(fr.getPageSize(), "default pageSize should be null");
    assertNull(fr.getTotal(), "default total should be null");
  }
}
