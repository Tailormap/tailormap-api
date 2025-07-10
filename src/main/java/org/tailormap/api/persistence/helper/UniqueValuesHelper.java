/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.persistence.helper;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Set;
import java.util.TreeSet;
import org.geotools.api.data.Query;
import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.api.filter.Filter;
import org.geotools.api.filter.FilterFactory;
import org.geotools.api.filter.expression.Function;
import org.geotools.api.filter.sort.SortOrder;
import org.geotools.filter.text.cql2.CQLException;
import org.geotools.filter.text.ecql.ECQL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.tailormap.api.geotools.featuresources.FeatureSourceFactoryHelper;
import org.tailormap.api.persistence.TMFeatureType;
import org.tailormap.api.viewer.model.UniqueValuesResponse;

public class UniqueValuesHelper {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public static UniqueValuesResponse getUniqueValues(
      TMFeatureType tmft,
      String attributeName,
      String filter,
      FilterFactory ff,
      FeatureSourceFactoryHelper featureSourceFactoryHelper,
      Boolean useGeotoolsUniqueFunction) {
    final UniqueValuesResponse uniqueValuesResponse = new UniqueValuesResponse().filterApplied(false);
    SimpleFeatureSource fs = null;
    try {
      Filter existingFilter = null;
      if (null != filter) {
        existingFilter = ECQL.toFilter(filter);
      }
      logger.trace("existingFilter: {}", existingFilter);

      Filter notNull = ff.not(ff.isNull(ff.property(attributeName)));
      Filter f = notNull;
      if (null != existingFilter) {
        f = ff.and(notNull, existingFilter);
        uniqueValuesResponse.filterApplied(true);
      }

      Query q = new Query(tmft.getName(), f);
      q.setPropertyNames(attributeName);
      q.setSortBy(ff.sort(attributeName, SortOrder.ASCENDING));
      logger.trace("Unique values query: {}", q);

      fs = featureSourceFactoryHelper.openGeoToolsFeatureSource(tmft);
      // and then there are 2 scenarios:
      // there might be a performance benefit for one or the other
      if (!useGeotoolsUniqueFunction) {
        // #1 use a feature visitor to get the unique values
        // not recommended, as it may not be performant
        logger.trace("Using feature visitor to get unique values");
        fs.getFeatures(q)
            .accepts(
                feature -> uniqueValuesResponse.addValuesItem(
                    feature.getProperty(attributeName).getValue()),
                null);
      } else {
        // #2 or use a Function to get the unique values
        // this is the recommended way, uses SQL "distinct"
        logger.trace("Using geotools unique collection function to get unique values");
        Function unique = ff.function("Collection_Unique", ff.property(attributeName));
        Object o = unique.evaluate(fs.getFeatures(q));
        if (o instanceof Set<?> uniqueValues) {
          uniqueValuesResponse.setValues(new TreeSet<>(uniqueValues));
        }
      }
    } catch (CQLException e) {
      logger.error("Could not parse requested filter", e);
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not parse requested filter");
    } catch (IOException e) {
      logger.error("Could not retrieve attribute data", e);
    } finally {
      if (fs != null) {
        fs.getDataStore().dispose();
      }
    }

    return uniqueValuesResponse;
  }
}
