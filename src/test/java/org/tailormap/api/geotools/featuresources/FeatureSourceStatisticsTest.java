/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.geotools.featuresources;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.feature.SchemaException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tailormap.api.StaticTestData;

class FeatureSourceStatisticsTest {

  private static final int randomFeatureCount = 10001;
  private SimpleFeatureSource randomFeatureSource = null;
  private long minEpochMillis;
  private long maxEpochMillis;
  private static final int DAYS_IN_WEEK = 7;
  private static final int MILLISECONDS_IN_WEEK = DAYS_IN_WEEK * 24 * 60 * 60 * 1000;

  @BeforeEach
  void createRandomFeatures() throws IOException, SchemaException {
    minEpochMillis = LocalDateTime.of(2000, 1, 1, 0, 0)
        .toInstant(java.time.ZoneOffset.UTC)
        .toEpochMilli();
    maxEpochMillis = LocalDateTime.of(2025, 12, 31, 23, 59)
        .toInstant(java.time.ZoneOffset.UTC)
        .toEpochMilli();
    randomFeatureSource = StaticTestData.createRandomFeatureSource(
        randomFeatureCount, 155000, 463000, minEpochMillis, maxEpochMillis);
    assumeTrue(randomFeatureSource != null, "Failed to create random feature source");
  }

  @AfterEach
  void cleanup() {
    if (randomFeatureSource != null) {
      randomFeatureSource.getDataStore().dispose();
    }
  }

  @Test
  void get_number_statistics() {
    AtomicInteger progressCount = new AtomicInteger(0);
    Map<String, Object> statistics = FeatureSourceStatistics.getFeatureSourceStatistics(
        randomFeatureSource, "randomNumber", null, 10, progressCount::set);

    assertTrue(statistics.containsKey("min"));
    assertThat((double) statistics.get("min"), is(closeTo(0, 100)));
    assertTrue(statistics.containsKey("max"));
    assertThat((double) statistics.get("max"), is(closeTo(randomFeatureCount - 1, 100)));
    assertTrue(statistics.containsKey("sum"));
    assertTrue(statistics.containsKey("average"));
    assertThat((double) statistics.get("average"), is(closeTo((randomFeatureCount - 1) / 2.0, 100)));
    assertEquals(randomFeatureCount, statistics.get("count"));
    assertThat(randomFeatureCount, is(greaterThanOrEqualTo(progressCount.get())));
  }

  @Test
  void get_number_statistics_with_filter() {
    AtomicInteger progressCount = new AtomicInteger(0);
    Map<String, Object> statistics = FeatureSourceStatistics.getFeatureSourceStatistics(
        randomFeatureSource, "randomNumber", "randomNumber < 500", 10, progressCount::set);

    assertTrue(statistics.containsKey("min"));
    assertThat((double) statistics.get("min"), is(closeTo(0, 5)));
    assertTrue(statistics.containsKey("max"));
    assertThat((double) statistics.get("max"), is(closeTo(500 - 1, 5)));
    assertTrue(statistics.containsKey("sum"));
    assertTrue(statistics.containsKey("average"));
    assertThat((double) statistics.get("average"), is(closeTo((500 - 1) / 2.0, 10)));
    assertTrue(statistics.containsKey("count"));
    assertThat(((Number) statistics.get("count")).doubleValue(), is(closeTo(500, 50)));
    assertThat((int) statistics.get("count"), is(greaterThanOrEqualTo(progressCount.get())));
  }

  @Test
  @SuppressWarnings("JavaUtilDate")
  void get_date_statistics() {
    AtomicInteger progressCount = new AtomicInteger(0);
    Map<String, Object> statistics = FeatureSourceStatistics.getFeatureSourceStatistics(
        randomFeatureSource, "date", null, 10, progressCount::set);
    assertTrue(statistics.containsKey("min"));
    assertThat(
        (double) ((Date) statistics.get("min")).getTime(),
        is(closeTo((double) minEpochMillis, MILLISECONDS_IN_WEEK)));
    assertTrue(statistics.containsKey("max"));

    assertThat(
        (double) ((Date) statistics.get("max")).getTime(),
        is(closeTo((double) maxEpochMillis, MILLISECONDS_IN_WEEK)));
    assertThat((double) ((Date) statistics.get("max")).getTime(), is(greaterThan((double)
        ((Date) statistics.get("min")).getTime())));

    assertFalse(statistics.containsKey("sum"));
    assertFalse(statistics.containsKey("average"));

    assertEquals(randomFeatureCount, statistics.get("count"));
    assertThat(randomFeatureCount, is(greaterThanOrEqualTo(progressCount.get())));
  }

  @Test
  void get_temporal_statistics() {
    AtomicInteger progressCount = new AtomicInteger(0);
    long maxEpochDays = Instant.ofEpochMilli(maxEpochMillis)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
        .toEpochDay();
    long minEpochDays = Instant.ofEpochMilli(minEpochMillis)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
        .toEpochDay();
    Map<String, Object> statistics = FeatureSourceStatistics.getFeatureSourceStatistics(
        randomFeatureSource, "localdate", null, 10, progressCount::set);

    assertTrue(statistics.containsKey("min"));
    LocalDate min = (LocalDate) statistics.get("min");
    assertThat((double) min.toEpochDay(), is(closeTo((double) minEpochDays, DAYS_IN_WEEK)));

    assertTrue(statistics.containsKey("max"));
    LocalDate max = (LocalDate) statistics.get("max");
    assertThat((double) max.toEpochDay(), is(closeTo((double) maxEpochDays, DAYS_IN_WEEK)));

    assertThat((double) max.toEpochDay(), is(greaterThan((double) min.toEpochDay())));

    assertFalse(statistics.containsKey("sum"));
    assertFalse(statistics.containsKey("average"));

    assertEquals(randomFeatureCount, statistics.get("count"));
    assertThat(randomFeatureCount, is(greaterThanOrEqualTo(progressCount.get())));
  }

  @Test
  void get_geometry_statistics() {
    assertThrows(IllegalArgumentException.class, () -> {
      FeatureSourceStatistics.getFeatureSourceStatistics(randomFeatureSource, "location", null, 10, null);
    });
  }
}
