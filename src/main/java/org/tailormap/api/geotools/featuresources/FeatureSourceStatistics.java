/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.geotools.featuresources;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.time.temporal.Temporal;
import java.util.Date;
import java.util.function.IntConsumer;
import org.geotools.api.data.Query;
import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.feature.type.AttributeType;
import org.geotools.api.filter.Filter;
import org.geotools.feature.visitor.AverageVisitor;
import org.geotools.feature.visitor.CountVisitor;
import org.geotools.feature.visitor.MaxVisitor;
import org.geotools.feature.visitor.MinVisitor;
import org.geotools.feature.visitor.SumVisitor;
import org.geotools.filter.text.cql2.CQLException;
import org.geotools.filter.text.ecql.ECQL;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tailormap.api.geotools.collection.ProgressReportingFeatureCollection;
import org.tailormap.api.viewer.model.AttributeStatisticsResponse;

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
   * reporting via a callback. Don't forget to dispose of the datastore, using e.g.
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
  @NonNull public static AttributeStatisticsResponse getFeatureSourceStatistics(
      @NonNull SimpleFeatureSource featureSource,
      @NonNull String attributeName,
      @Nullable String filterCQL,
      int progressInterval,
      @Nullable IntConsumer progressCallback) {
    AttributeStatisticsResponse featureSourceStatistics = new AttributeStatisticsResponse();

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
    SimpleFeatureType simpleFeatureType = featureSource.getSchema();
    Query query = new Query(simpleFeatureType.getTypeName());
    query.setHandle("calculateAttributeStatistics");
    query.setPropertyNames(attributeName);

    if (filterCQL != null && !filterCQL.isEmpty()) {
      try {
        Filter parsedFilter = ECQL.toFilter(filterCQL);
        query.setFilter(parsedFilter);
        featureSourceStatistics.filterApplied(true);
      } catch (CQLException e) {
        logger.error("Invalid filter cql: {} ", filterCQL);
        return featureSourceStatistics;
      }
    }

    try {
      ProgressReportingFeatureCollection featureCollection = new ProgressReportingFeatureCollection(
          featureSource.getFeatures(query), progressInterval, progressCallback);

      MaxVisitor maxVisitor = new MaxVisitor(attributeName, simpleFeatureType);
      MinVisitor minVisitor = new MinVisitor(attributeName, simpleFeatureType);
      CountVisitor countVisitor = new CountVisitor();
      featureCollection.accepts(maxVisitor, null);
      featureCollection.accepts(minVisitor, null);
      featureCollection.accepts(countVisitor, null);

      featureSourceStatistics.max(
          maxVisitor.getResult() != null ? maxVisitor.getResult().getValue() : null);
      featureSourceStatistics.min(
          minVisitor.getResult() != null ? minVisitor.getResult().getValue() : null);
      featureSourceStatistics.count(
          countVisitor.getResult() != null ? countVisitor.getResult().toLong() : null);

      if (isNumeric) {
        AverageVisitor averageVisitor = new AverageVisitor(attributeName, simpleFeatureType);
        SumVisitor sumVisitor = new SumVisitor(attributeName, simpleFeatureType);

        featureCollection.accepts(averageVisitor, null);
        featureCollection.accepts(sumVisitor, null);

        featureSourceStatistics.avg(
            averageVisitor.getResult() != null
                ? averageVisitor.getResult().toDouble()
                : null);

        featureSourceStatistics.sum(
            sumVisitor.getResult() != null ? sumVisitor.getResult().toDouble() : null);
      }
    } catch (IOException e) {
      logger.error("Error calculating statistics for attribute {}", attributeName, e);
    }

    return featureSourceStatistics;
  }
}
