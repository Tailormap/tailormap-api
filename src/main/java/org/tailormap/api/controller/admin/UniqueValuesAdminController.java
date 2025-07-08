package org.tailormap.api.controller.admin;

import java.io.Serializable;
import org.geotools.api.filter.FilterFactory;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.util.factory.GeoTools;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.tailormap.api.geotools.featuresources.FeatureSourceFactoryHelper;
import org.tailormap.api.persistence.TMFeatureType;
import org.tailormap.api.persistence.helper.UniqueValuesHelper;
import org.tailormap.api.repository.FeatureTypeRepository;
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

  @GetMapping(
      path = "${tailormap-api.admin.base-path}/unique-values/{featureTypeId}/{attributeName}",
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Serializable> getUniqueValues(
      @PathVariable Long featureTypeId,
      @PathVariable String attributeName,
      @RequestParam(required = false) String filter) {
    TMFeatureType tmft = this.featureTypeRepository
        .findById(featureTypeId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Feature type not found"));
    UniqueValuesResponse response = UniqueValuesHelper.getUniqueValues(
        tmft, attributeName, filter, ff, featureSourceFactoryHelper, useGeotoolsUniqueFunction);
    return ResponseEntity.status(HttpStatus.OK).body(response);
  }
}
