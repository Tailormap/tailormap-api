/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.io.IOException;
import org.apache.commons.lang3.StringUtils;
import org.geotools.api.data.SimpleFeatureSource;
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
import org.tailormap.api.geotools.featuresources.FeatureSourceStatistics;
import org.tailormap.api.persistence.TMFeatureType;
import org.tailormap.api.repository.FeatureTypeRepository;
import org.tailormap.api.viewer.model.AttributeStatisticsResponse;
import org.tailormap.api.viewer.model.ErrorResponse;

@RestController
public class AttributeStatisticsAdminController {
  private final FeatureTypeRepository featureTypeRepository;
  private final FeatureSourceFactoryHelper featureSourceFactoryHelper;

  public AttributeStatisticsAdminController(
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

  /**
   * Retrieve attribute statistics for a feature type.
   *
   * @param featureTypeId the feature type id for which to retrieve statistics
   * @param attributeName the name of the attribute
   * @param filter an optional CQL filter to apply to the features before calculating statistics
   * @return @return the calculated statistics; fields may be null when not applicable or when no features match
   * @throws ResponseStatusException if the feature type or attribute does not exist, or if a bad request was
   *     submitted
   */
  @Operation(
      summary = "Retrieve attribute statistics for a feature type",
      description = "Retrieve statistics (min, max, average, sum, count) for a given attribute of a feature type."
          + " Optionally, a CQL filter can be applied to filter the features before calculating the"
          + " statistics.")
  @GetMapping(
      path = "${tailormap-api.admin.base-path}/statistics/{featureTypeId}/{attributeName}",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @ApiResponse(
      responseCode = "200",
      description = "Statistics retrieved successfully",
      content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(example = """
{"min":85746,"max":100000,"average":92873,"sum":371492,"count":4}
""")))
  public ResponseEntity<AttributeStatisticsResponse> getStatistics(
      @PathVariable Long featureTypeId,
      @PathVariable String attributeName,
      @RequestParam(required = false) String filter) {

    if (StringUtils.isBlank(attributeName)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attribute name is required");
    }

    TMFeatureType tmft = this.featureTypeRepository
        .findById(featureTypeId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Feature type not found"));

    if (tmft.getAttributeByName(attributeName).isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Attribute does not exist in feature type: " + attributeName);
    }
    SimpleFeatureSource featureSource = null;
    try {
      featureSource = featureSourceFactoryHelper.openGeoToolsFeatureSource(tmft);
      return ResponseEntity.ok()
          .body(FeatureSourceStatistics.getFeatureSourceStatistics(
              featureSource, attributeName, filter, 10, null));
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
    } catch (IOException e) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error accessing feature source", e);
    } finally {
      if (featureSource != null) {
        featureSource.getDataStore().dispose();
      }
    }
  }
}
