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
import java.util.Date;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.data.DataUtilities;
import org.geotools.data.memory.MemoryDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.SchemaException;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

class ProgressReportingFeatureCollectionTest {
  private static final int randomFeatureCount = 104;
  private SimpleFeatureSource randomFeatureSource = null;

  @BeforeEach
  void createRandomFeatures() throws IOException, SchemaException {
    SimpleFeatureType inputType =
        DataUtilities.createType("test", "id:Integer,label:String,date:Date,location:Point:28992");
    MemoryDataStore dataStore = new MemoryDataStore(inputType);

    int[] xCoords = new Random().ints(randomFeatureCount, 155000, 165000).toArray();
    int[] yCoords = new Random().ints(randomFeatureCount, 463000, 473000).toArray();
    long minEpoch = LocalDate.of(2000, 1, 1).toEpochDay() * 86400L * 1000L;
    long maxEpoch = LocalDate.of(2025, 12, 31).toEpochDay() * 86400L * 1000L;
    final Random random = new Random();
    final SimpleFeatureBuilder fb = new SimpleFeatureBuilder(inputType);
    final GeometryFactory gf = new GeometryFactory();
    IntStream.range(0, randomFeatureCount).forEach(id -> {
      fb.set("id", id);
      fb.set("label", "Feature number " + id);
      @SuppressWarnings("JavaUtilDate")
      Date randomDate = new Date(minEpoch + (long) (random.nextDouble() * (maxEpoch - minEpoch)));
      fb.set("date", randomDate);
      fb.set("location", gf.createPoint(new Coordinate(xCoords[id], yCoords[id])));
      dataStore.addFeature(fb.buildFeature(String.valueOf(id)));
    });

    randomFeatureSource = dataStore.getFeatureSource(inputType.getName());
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
