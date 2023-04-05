/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import java.io.Serializable;
import java.util.stream.Collectors;
import nl.b3p.tailormap.api.annotation.AppRestController;
import nl.b3p.tailormap.api.persistence.GeoService;
import nl.b3p.tailormap.api.persistence.TMFeatureType;
import nl.b3p.tailormap.api.persistence.json.AppLayerRef;
import nl.b3p.tailormap.api.persistence.json.GeoServiceLayer;
import nl.b3p.tailormap.api.persistence.json.TMAttributeDescriptor;
import nl.b3p.tailormap.api.persistence.json.TMAttributeType;
import nl.b3p.tailormap.api.persistence.json.TMGeometryType;
import nl.b3p.tailormap.api.repository.FeatureSourceRepository;
import nl.b3p.tailormap.api.viewer.model.Attribute;
import nl.b3p.tailormap.api.viewer.model.LayerDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

import static nl.b3p.tailormap.api.persistence.helper.TMAttributeTypeHelper.isGeometry;

@AppRestController
@Validated
@RequestMapping(
    path = "${tailormap-api.base-path}/{viewerKind}/{viewerName}/layer/{appLayerId}/describe",
    produces = MediaType.APPLICATION_JSON_VALUE)
public class LayerDescriptionController {

  private final FeatureSourceRepository featureSourceRepository;

  public LayerDescriptionController(FeatureSourceRepository featureSourceRepository) {
    this.featureSourceRepository = featureSourceRepository;
  }

  @Transactional
  @GetMapping
  public ResponseEntity<Serializable> getAppLayerDescription(
      @ModelAttribute AppLayerRef ref,
      @ModelAttribute GeoService service,
      @ModelAttribute GeoServiceLayer layer) {

    if (layer == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Can't find app layer ref " + ref);
    }

    TMFeatureType tmft = service.findFeatureTypeForLayer(layer, featureSourceRepository);
    if (tmft == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Layer does not have feature type");
    }

    LayerDetails r =
        new LayerDetails()
            .id(ref.getId())
            .serviceId(ref.getServiceId())
            .featureTypeName(tmft.getName())
            .geometryAttribute(tmft.getDefaultGeometryAttribute())
            .geometryType(
                tmft.getDefaultGeometryDescriptor()
                    .map(TMAttributeDescriptor::getType)
                    .map(TMAttributeType::getValue)
                    .map(TMGeometryType::fromValue)
                    .orElse(null))
            .attributes(
                tmft.getAttributes().stream()
                    .map(
                        a ->
                            new Attribute()
                                .name(a.getName())
                                // Only return generic 'geometry' type for now, frontend doesn't
                                // handle different geometry types. For the default geometry
                                // attribute there is a specific geometry type set
                                .type(isGeometry(a.getType()) ? TMAttributeType.GEOMETRY : a.getType()))
                    .collect(Collectors.toList()));
    return ResponseEntity.ok(r);
  }
}
