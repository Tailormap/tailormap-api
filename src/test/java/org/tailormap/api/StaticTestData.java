/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Properties;
import java.util.Random;
import java.util.stream.IntStream;
import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.data.DataUtilities;
import org.geotools.data.memory.MemoryDataStore;
import org.geotools.feature.SchemaException;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;

/**
 * setup testdata from property file.
 *
 * @author mprins
 */
public class StaticTestData {
  public static final Properties testData = new Properties();

  static {
    try {
      testData.load(StaticTestData.class.getResourceAsStream("/StaticTestData.properties"));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static String get(String key) {
    return testData.getProperty(key);
  }

  public static String getResourceString(Resource resource) throws IOException {
    return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
  }

  /**
   * Create a random feature source with the given number of features, within the given (EPSG:28992) bounds and date
   * range.
   *
   * @param randomFeatureCount the number of random features to create
   * @param minX the minimum X coordinate, the maximum X coordinate will be 10000 greater
   * @param minY the minimum Y coordinate, the maximum Y coordinate will be 10000 greater
   * @param minEpochMillis the minimum epoch milliseconds for the date range
   * @param maxEpochMillis the maximum epoch milliseconds for the date range
   * @return a SimpleFeatureSource containing the generated features
   * @throws IOException if an I/O error occurs
   * @throws SchemaException if there is an error creating the feature type
   */
  public static SimpleFeatureSource createRandomFeatureSource(
      int randomFeatureCount, int minX, int minY, long minEpochMillis, long maxEpochMillis)
      throws IOException, SchemaException {
    final Random random = new Random();
    SimpleFeatureType inputType = DataUtilities.createType(
        "test",
        "id:Integer,randomNumber:Long,date:Date,localdate:java.time.LocalDate,*location:Point:srid=28992");
    MemoryDataStore dataStore = new MemoryDataStore(inputType);

    int[] xCoords = random.ints(randomFeatureCount, minX, minX + 10000).toArray();
    int[] yCoords = random.ints(randomFeatureCount, minY, minY + 10000).toArray();
    final SimpleFeatureBuilder fb = new SimpleFeatureBuilder(inputType);
    final GeometryFactory gf = new GeometryFactory();
    IntStream.range(0, randomFeatureCount).forEach(id -> {
      Instant randomDate = Instant.ofEpochMilli(random.nextLong(minEpochMillis, maxEpochMillis));

      fb.set("id", id);
      fb.set("randomNumber", random.nextInt(randomFeatureCount - 1));
      fb.set("date", randomDate.toEpochMilli());
      fb.set("localdate", randomDate.atZone(ZoneOffset.UTC).toLocalDate());
      fb.set("location", gf.createPoint(new Coordinate(xCoords[id], yCoords[id])));

      dataStore.addFeature(fb.buildFeature(String.valueOf(id)));
    });

    return dataStore.getFeatureSource(inputType.getName());
  }
}
