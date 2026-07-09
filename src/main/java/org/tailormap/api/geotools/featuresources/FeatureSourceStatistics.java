/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.geotools.featuresources;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.time.temporal.ChronoField;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;
import org.geotools.api.data.Query;
import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.api.feature.type.AttributeType;
import org.geotools.api.filter.Filter;
import org.geotools.filter.text.cql2.CQLException;
import org.geotools.filter.text.ecql.ECQL;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tailormap.api.geotools.collection.ProgressReportingFeatureIterator;

/** Calculate statistics (min/max/average/sum) and metadata (count) for an attribute of a feature source. */
public class FeatureSourceStatistics {

  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private FeatureSourceStatistics() {
    // utility class
  }

  /**
   * Determine statistics for a given attribute of a feature source, optionally filtered by a CQL filter. The
   * statistics include min, max, average, sum (for numeric attributes), and count. The method also supports progress
   * reporting via a callback. Don't forget to dispose of the datastore, using eg.
   * {@code featureSource.getDataStore().dispose();}
   *
   * @param featureSource the feature source to calculate statistics for
   * @param attributeName the name of the attribute to calculate statistics for
   * @param filterCQL an optional CQL filter to apply to the feature source
   * @param progressInterval the interval at which to report progress, must be greater than 0
   * @param progressCallback an optional callback to report progress
   * @return a map containing the calculated statistics, the map may be empty
   */
  @SuppressWarnings("JavaUtilDate")
  @NonNull public static Map<String, Object> getFeatureSourceStatistics(
      @NonNull SimpleFeatureSource featureSource,
      @NonNull String attributeName,
      @Nullable String filterCQL,
      int progressInterval,
      @Nullable IntConsumer progressCallback) {
    Map<String, Object> featureSourceStatistics = new LinkedHashMap<>();

    if (progressInterval <= 0 && progressCallback != null) {
      throw new IllegalArgumentException("Progress interval must be greater than 1");
    }

    AttributeType aType = featureSource.getSchema().getType(attributeName);
    boolean isNumeric = Number.class.isAssignableFrom(aType.getBinding());
    boolean isDate =
        Date.class.isAssignableFrom(aType.getBinding()) || Temporal.class.isAssignableFrom(aType.getBinding());

    if (!isNumeric && !isDate) {
      throw new IllegalArgumentException("Attribute " + attributeName + " is not numeric or date");
    }

    Query query = new Query(featureSource.getSchema().getTypeName());
    query.setHandle("calculateAttributeStatistics");
    query.setPropertyNames(attributeName);

    if (filterCQL != null && !filterCQL.isEmpty()) {
      try {
        Filter parsedFilter = ECQL.toFilter(filterCQL);
        query.setFilter(parsedFilter);
      } catch (CQLException e) {
        throw new RuntimeException(e);
      }
    }

    List<Number> numbers = new ArrayList<>();
    int counted = 0;
    try (ProgressReportingFeatureIterator featureIterator = new ProgressReportingFeatureIterator(
        featureSource.getFeatures(query).features(), progressInterval, progressCallback)) {
      while (featureIterator.hasNext()) {
        counted++;
        Object value = featureIterator.next().getAttribute(attributeName);
        if (isNumeric && value instanceof Number numberValue) {
          numbers.add(numberValue);
          featureSourceStatistics.merge(
              "min",
              numberValue,
              (oldVal, newVal) ->
                  Math.min(((Number) oldVal).doubleValue(), ((Number) newVal).doubleValue()));
          featureSourceStatistics.merge(
              "max",
              numberValue,
              (oldVal, newVal) ->
                  Math.max(((Number) oldVal).doubleValue(), ((Number) newVal).doubleValue()));
          featureSourceStatistics.merge(
              "sum",
              numberValue,
              (oldVal, newVal) -> ((Number) oldVal).doubleValue() + ((Number) newVal).doubleValue());
        }
        if (isDate && value instanceof Temporal dateValue) {
          ChronoField field;
          if (dateValue.isSupported(ChronoField.INSTANT_SECONDS)) {
            field = ChronoField.INSTANT_SECONDS;
          } else {
            field = ChronoField.EPOCH_DAY;
          }
          featureSourceStatistics.merge(
              "min",
              dateValue,
              (oldVal, newVal) -> ((Temporal) oldVal).getLong(field) <= ((Temporal) newVal).getLong(field)
                  ? oldVal
                  : newVal);
          featureSourceStatistics.merge(
              "max",
              dateValue,
              (oldVal, newVal) -> ((Temporal) oldVal).getLong(field) >= ((Temporal) newVal).getLong(field)
                  ? oldVal
                  : newVal);
        }

        if (isDate && value instanceof Date dateValue) {
          featureSourceStatistics.merge(
              "min",
              dateValue,
              (oldVal, newVal) -> ((Date) oldVal).before((Date) newVal) ? oldVal : newVal);
          featureSourceStatistics.merge(
              "max",
              dateValue,
              (oldVal, newVal) -> ((Date) oldVal).after((Date) newVal) ? oldVal : newVal);
        }
      }
    } catch (IOException e) {
      logger.error("Error calculating statistics for attribute {}", attributeName, e);
    }

    numbers.stream()
        .mapToDouble(Number::doubleValue)
        .average()
        .ifPresent(avg -> featureSourceStatistics.put("average", avg));

    featureSourceStatistics.put("count", counted);

    return featureSourceStatistics;
  }
}
