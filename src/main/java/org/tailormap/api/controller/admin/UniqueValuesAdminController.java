/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller.admin;

import java.io.Serializable;
import org.apache.commons.lang3.StringUtils;
import org.geotools.api.filter.FilterFactory;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.util.factory.GeoTools;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.tailormap.api.geotools.featuresources.FeatureSourceFactoryHelper;
import org.tailormap.api.persistence.TMFeatureType;
import org.tailormap.api.persistence.helper.UniqueValuesHelper;
import org.tailormap.api.persistence.json.TMAttributeDescriptor;
import org.tailormap.api.repository.FeatureTypeRepository;
import org.tailormap.api.viewer.model.ErrorResponse;
import org.tailormap.api.viewer.model.UniqueValuesResponse;

@RestController
public class UniqueValuesAdminController {
  private final FeatureTypeRepository featureTypeRepository;

  private final FeatureSourceFactoryHelper featureSourceFactoryHelper;

  @Value("${tailormap-api.unique.use_geotools_unique_function:true}")
  private boolean useGeotoolsUniqueFunction;

  private final FilterFactory ff = CommonFactoryFinder.getFilterFactory(GeoTools.getDefaultHints());

  public UniqueValuesAdminController(
      FeatureTypeRepository featureTypeRepository, FeatureSourceFactoryHelper featureSourceFactoryHelper) {
    this.featureTypeRepository = featureTypeRepository;
    this.featureSourceFactoryHelper = featureSourceFactoryHelper;
  }

  @ExceptionHandler({ResponseStatusException.class})
  public ResponseEntity<?> handleException(ResponseStatusException ex) {
    // wrap the exception in a proper json response
    return ResponseEntity.status(ex.getStatusCode())
        .contentType(MediaType.APPLICATION_JSON)
        .body(new ErrorResponse()
            .message(
                ex.getReason() != null
                    ? ex.getReason()
                    : ex.getBody().getTitle())
            .code(ex.getStatusCode().value()));
  }

  @GetMapping(
      path = "${tailormap-api.admin.base-path}/unique-values/{featureTypeId}/{attributeName}",
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Serializable> getUniqueValues(
      @PathVariable Long featureTypeId,
      @PathVariable String attributeName,
      @RequestParam(required = false) String filter)
      throws ResponseStatusException {
    if (StringUtils.isBlank(attributeName)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attribute name is required");
    }

    TMFeatureType tmft = this.featureTypeRepository
        .findById(featureTypeId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Feature type not found"));

    if (tmft.getAttributeByName(attributeName).isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attribute does not exist: " + tmft.getAttributes().stream().map(TMAttributeDescriptor::getName).toList());
    }

    UniqueValuesResponse response = UniqueValuesHelper.getUniqueValues(
        tmft, attributeName, filter, ff, featureSourceFactoryHelper, useGeotoolsUniqueFunction);
    return ResponseEntity.status(HttpStatus.OK).body(response);
  }
}
