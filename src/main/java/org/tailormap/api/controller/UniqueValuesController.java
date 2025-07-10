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
import java.io.Serializable;
import org.apache.commons.lang3.StringUtils;
import org.geotools.api.filter.FilterFactory;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.util.factory.GeoTools;
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
import org.tailormap.api.persistence.helper.UniqueValuesHelper;
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
    UniqueValuesResponse uniqueValuesResponse = UniqueValuesHelper.getUniqueValues(
        tmft, attributeName, filter, ff, featureSourceFactoryHelper, useGeotoolsUniqueFunction);
    return ResponseEntity.status(HttpStatus.OK).body(uniqueValuesResponse);
  }
}
