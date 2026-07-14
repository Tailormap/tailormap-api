/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.geotools.collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.SchemaException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tailormap.api.StaticTestData;

class ProgressReportingFeatureCollectionTest {
  private static final int randomFeatureCount = 104;
  private SimpleFeatureSource randomFeatureSource = null;

  @BeforeEach
  void createRandomFeatures() throws IOException, SchemaException {
    randomFeatureSource = StaticTestData.createRandomFeatureSource(
        randomFeatureCount,
        155000,
        463000,
        LocalDate.of(2000, 1, 1).toEpochDay() * 86400L * 1000L,
        LocalDate.of(2025, 12, 31).toEpochDay() * 86400L * 1000L);
    assumeTrue(randomFeatureSource != null, "Failed to create random feature source");
  }

  @AfterEach
  void cleanup() {
    if (randomFeatureSource != null) {
      randomFeatureSource.getDataStore().dispose();
    }
  }

  @Test
  void test_progress_works() throws IOException, SchemaException {
    SimpleFeatureCollection source = randomFeatureSource.getFeatures();
    AtomicInteger progressCount = new AtomicInteger(0);
    ProgressReportingFeatureCollection collection =
        new ProgressReportingFeatureCollection(source, 10, progressCount::set);
    assertEquals(randomFeatureCount, collection.size());
    try (SimpleFeatureIterator iterator = collection.features()) {
      while (iterator.hasNext()) {
        iterator.next();
      }
      assertEquals(10 * (randomFeatureCount / 10), progressCount.get());
    }
  }

  @Test
  void allows_null_progress() throws IOException {
    SimpleFeatureCollection source = randomFeatureSource.getFeatures();
    ProgressReportingFeatureCollection collection = new ProgressReportingFeatureCollection(source, 10, null);
    assertEquals(randomFeatureCount, collection.size());
    try (SimpleFeatureIterator iterator = collection.features()) {
      while (iterator.hasNext()) {
        iterator.next();
      }
    }
  }

  @Test
  void disallows_negative_progress_interval() throws IOException {
    SimpleFeatureCollection source = randomFeatureSource.getFeatures();
    assertThrows(
        IllegalArgumentException.class, () -> new ProgressReportingFeatureCollection(source, -1, count -> {}));
  }
}
