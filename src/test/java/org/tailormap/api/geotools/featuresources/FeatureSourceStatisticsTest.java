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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.feature.SchemaException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tailormap.api.StaticTestData;
import org.tailormap.api.viewer.model.AttributeStatisticsResponse;

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
    AttributeStatisticsResponse statistics = FeatureSourceStatistics.getFeatureSourceStatistics(
        randomFeatureSource, "randomNumber", null, 10, progressCount::set);

    assertFalse(statistics.getFilterApplied());
    assertNotNull(statistics.getMin());
    assertThat(Double.valueOf(statistics.getMin().toString()), is(closeTo(0, 100)));
    assertNotNull(statistics.getMax());
    assertThat(Double.valueOf(statistics.getMax().toString()), is(closeTo(randomFeatureCount - 1, 100)));
    assertNotNull(statistics.getSum());
    assertNotNull(statistics.getAvg());
    assertThat(statistics.getAvg(), is(closeTo((randomFeatureCount - 1) / 2.0, 100)));
    assertEquals(randomFeatureCount, statistics.getCount());
    assertThat(randomFeatureCount, is(greaterThanOrEqualTo(progressCount.get())));
  }

  @Test
  void get_number_statistics_with_filter() {
    AtomicInteger progressCount = new AtomicInteger(0);
    AttributeStatisticsResponse statistics = FeatureSourceStatistics.getFeatureSourceStatistics(
        randomFeatureSource, "randomNumber", "randomNumber < 500", 10, progressCount::set);

    assertTrue(statistics.getFilterApplied());
    assertNotNull(statistics.getMin());
    assertThat(Double.valueOf(statistics.getMin().toString()), is(closeTo(0, 5)));
    assertNotNull(statistics.getMax());
    assertThat(Double.valueOf(statistics.getMax().toString()), is(closeTo(500 - 1, 5)));
    assertNotNull(statistics.getSum());
    assertNotNull(statistics.getAvg());
    assertThat(statistics.getAvg(), is(closeTo((500 - 1) / 2.0, 10)));
    assertNotNull(statistics.getCount());
    assertThat(statistics.getCount().doubleValue(), is(closeTo(500, 50)));
    assertThat(statistics.getCount().intValue(), is(greaterThanOrEqualTo(progressCount.get())));
  }

  @Test
  @SuppressWarnings("JavaUtilDate")
  void get_date_statistics() {
    AtomicInteger progressCount = new AtomicInteger(0);
    AttributeStatisticsResponse statistics = FeatureSourceStatistics.getFeatureSourceStatistics(
        randomFeatureSource, "date", null, 10, progressCount::set);

    assertFalse(statistics.getFilterApplied());
    assertNotNull(statistics.getMin());
    assertThat(
        (double) ((Date) statistics.getMin()).getTime(),
        is(closeTo((double) minEpochMillis, MILLISECONDS_IN_WEEK)));
    assertNotNull(statistics.getMax());
    assertThat(
        (double) ((Date) statistics.getMax()).getTime(),
        is(closeTo((double) maxEpochMillis, MILLISECONDS_IN_WEEK)));
    assertThat((double) ((Date) statistics.getMax()).getTime(), is(greaterThan((double)
        ((Date) statistics.getMin()).getTime())));
    assertNull(statistics.getSum());
    assertNull(statistics.getAvg());
    assertEquals(randomFeatureCount, statistics.getCount());
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
    AttributeStatisticsResponse statistics = FeatureSourceStatistics.getFeatureSourceStatistics(
        randomFeatureSource, "localdate", null, 10, progressCount::set);

    assertFalse(statistics.getFilterApplied());
    assertNotNull(statistics.getMin());
    LocalDate min = (LocalDate) statistics.getMin();
    assertThat((double) min.toEpochDay(), is(closeTo((double) minEpochDays, DAYS_IN_WEEK)));

    assertNotNull(statistics.getMax());
    LocalDate max = (LocalDate) statistics.getMax();
    assertThat((double) max.toEpochDay(), is(closeTo((double) maxEpochDays, DAYS_IN_WEEK)));

    assertThat((double) max.toEpochDay(), is(greaterThan((double) min.toEpochDay())));

    assertNull(statistics.getSum());
    assertNull(statistics.getAvg());

    assertEquals(randomFeatureCount, statistics.getCount());
    assertThat(randomFeatureCount, is(greaterThanOrEqualTo(progressCount.get())));
  }

  @Test
  void get_geometry_statistics() {
    assertThrows(
        IllegalArgumentException.class,
        () -> FeatureSourceStatistics.getFeatureSourceStatistics(
            randomFeatureSource, "location", null, 10, null));
  }
}
