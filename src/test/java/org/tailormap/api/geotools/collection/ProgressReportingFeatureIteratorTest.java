/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.geotools.collection;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.geotools.data.simple.SimpleFeatureIterator;
import org.junit.jupiter.api.Test;

class ProgressReportingFeatureIteratorTest {
  @Test
  void disallows_negative_progress_interval() {
    assertThrows(IllegalArgumentException.class, () -> {
      try (SimpleFeatureIterator ignored = new ProgressReportingFeatureIterator(null, -1, null)) {
        // ignored
      }
    });
  }
}
