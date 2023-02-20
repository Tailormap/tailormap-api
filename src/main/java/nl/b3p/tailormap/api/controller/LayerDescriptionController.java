/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import java.io.Serializable;
import java.util.stream.Collectors;
import nl.b3p.tailormap.api.annotation.AppRestController;
import nl.b3p.tailormap.api.persistence.Application;
import nl.b3p.tailormap.api.persistence.TMAttributeDescriptor;
import nl.b3p.tailormap.api.persistence.TMFeatureType;
import nl.b3p.tailormap.api.persistence.json.AppLayerRef;
import nl.b3p.tailormap.api.persistence.json.GeoServiceLayer;
import nl.b3p.tailormap.api.persistence.json.TMAttributeType;
import nl.b3p.tailormap.api.persistence.json.TMGeometryType;
import nl.b3p.tailormap.api.viewer.model.Attribute;
import nl.b3p.tailormap.api.viewer.model.LayerDetails;
import nl.b3p.tailormap.api.viewer.model.Service;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;

@AppRestController
@Validated
@RequestMapping(
    path = "${tailormap-api.base-path}/app/{appId}/layer/{appLayerId}/describe",
    produces = MediaType.APPLICATION_JSON_VALUE)
public class LayerDescriptionController {

  @GetMapping
  public ResponseEntity<Serializable> getAppLayerDescription(
      @ModelAttribute Application application,
      @ModelAttribute AppLayerRef ref,
      @ModelAttribute Service service,
      @ModelAttribute GeoServiceLayer layer,
      @ModelAttribute TMFeatureType featureType) {

    if (layer == null) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Can't find app layer ref %s" + ref);
    }

    if (featureType == null) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Layer does not have feature type");
    }

    LayerDetails r =
        new LayerDetails()
            .id(ref.getId())
            .serviceId(ref.getServiceId())
            .featureTypeName(featureType.getName())
            .geometryAttribute(featureType.getDefaultGeometryAttribute())
            .geometryType(
                featureType
                    .getDefaultGeometryDescriptor()
                    .map(TMAttributeDescriptor::getType)
                    .map(TMAttributeType::getValue)
                    .map(TMGeometryType::fromValue)
                    .orElse(null))
            .attributes(
                featureType.getAttributes().stream()
                    .map(
                        a ->
                            new Attribute()
                                .name(a.getName())
                                // Only return generic 'geometry' type for now, frontend doesn't
                                // handle different geometry types yet
                                .type(a.isGeometry() ? TMAttributeType.GEOMETRY : a.getType()))
                    .collect(Collectors.toList()));
    return ResponseEntity.ok(r);
  }
}
