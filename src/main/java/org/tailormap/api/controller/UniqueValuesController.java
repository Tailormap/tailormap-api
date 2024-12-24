/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;
import static org.tailormap.api.persistence.helper.TMFeatureTypeHelper.getNonHiddenAttributeNames;

import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import java.io.IOException;
import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.util.Set;
import java.util.TreeSet;
import org.apache.commons.lang3.StringUtils;
import org.geotools.api.data.Query;
import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.api.filter.Filter;
import org.geotools.api.filter.FilterFactory;
import org.geotools.api.filter.expression.Function;
import org.geotools.api.filter.sort.SortOrder;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.filter.text.cql2.CQLException;
import org.geotools.filter.text.ecql.ECQL;
import org.geotools.util.factory.GeoTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.tailormap.api.annotation.AppRestController;
import org.tailormap.api.geotools.featuresources.FeatureSourceFactoryHelper;
import org.tailormap.api.persistence.Application;
import org.tailormap.api.persistence.GeoService;
import org.tailormap.api.persistence.TMFeatureType;
import org.tailormap.api.persistence.json.AppLayerSettings;
import org.tailormap.api.persistence.json.AppTreeLayerNode;
import org.tailormap.api.persistence.json.GeoServiceLayer;
import org.tailormap.api.repository.FeatureSourceRepository;
import org.tailormap.api.viewer.model.UniqueValuesResponse;

@AppRestController
@Validated
@RequestMapping(
    path = "${tailormap-api.base-path}/{viewerKind}/{viewerName}/layer/{appLayerId}/unique/{attributeName}",
    produces = MediaType.APPLICATION_JSON_VALUE)
public class UniqueValuesController {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final FeatureSourceFactoryHelper featureSourceFactoryHelper;

  private final FeatureSourceRepository featureSourceRepository;

  @Value("${tailormap-api.unique.use_geotools_unique_function:true}")
  private boolean useGeotoolsUniqueFunction;

  private final FilterFactory ff = CommonFactoryFinder.getFilterFactory(GeoTools.getDefaultHints());

  public UniqueValuesController(
      FeatureSourceFactoryHelper featureSourceFactoryHelper, FeatureSourceRepository featureSourceRepository) {
    this.featureSourceFactoryHelper = featureSourceFactoryHelper;
    this.featureSourceRepository = featureSourceRepository;
  }

  @Transactional
  @RequestMapping(method = {GET, POST})
  @Timed(value = "get_unique_attributes", description = "time spent to process get unique attributes call")
  @Counted(value = "get_unique_attributes", description = "number of unique attributes calls")
  public ResponseEntity<Serializable> getUniqueAttributes(
      @ModelAttribute GeoService service,
      @ModelAttribute GeoServiceLayer layer,
      @ModelAttribute Application app,
      @ModelAttribute AppTreeLayerNode appTreeLayerNode,
      @PathVariable("attributeName") String attributeName,
      @RequestParam(required = false) String filter) {
    if (StringUtils.isBlank(attributeName)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attribute name is required");
    }

    TMFeatureType tmft = service.findFeatureTypeForLayer(layer, featureSourceRepository);
    AppLayerSettings appLayerSettings = app.getAppLayerSettings(appTreeLayerNode);
    if (tmft == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Layer does not have feature type");
    }
    if (!getNonHiddenAttributeNames(tmft, appLayerSettings).contains(attributeName)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attribute does not exist");
    }
    UniqueValuesResponse uniqueValuesResponse = getUniqueValues(tmft, attributeName, filter);
    return ResponseEntity.status(HttpStatus.OK).body(uniqueValuesResponse);
  }

  private UniqueValuesResponse getUniqueValues(TMFeatureType tmft, String attributeName, String filter) {
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
        if (o instanceof Set) {
          @SuppressWarnings("unchecked")
          Set<Object> uniqueValues = (Set<Object>) o;
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
