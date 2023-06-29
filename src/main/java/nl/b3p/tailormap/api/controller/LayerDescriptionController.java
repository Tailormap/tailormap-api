/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import static nl.b3p.tailormap.api.persistence.helper.TMAttributeTypeHelper.isGeometry;

import java.io.Serializable;
import java.util.stream.Collectors;
import nl.b3p.tailormap.api.annotation.AppRestController;
import nl.b3p.tailormap.api.persistence.GeoService;
import nl.b3p.tailormap.api.persistence.TMFeatureType;
import nl.b3p.tailormap.api.persistence.json.AppTreeLayerNode;
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
      @ModelAttribute AppTreeLayerNode appTreeLayerNode,
      @ModelAttribute GeoService service,
      @ModelAttribute GeoServiceLayer layer) {

    if (layer == null) {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND, "Can't find layer " + appTreeLayerNode);
    }

    TMFeatureType tmft = service.findFeatureTypeForLayer(layer, featureSourceRepository);
    if (tmft == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Layer does not have feature type");
    }

    LayerDetails r =
        new LayerDetails()
            .id(appTreeLayerNode.getId())
            .serviceId(appTreeLayerNode.getServiceId())
            .featureTypeName(tmft.getName())
            .geometryAttribute(tmft.getDefaultGeometryAttribute())
            // TODO deduce from tmft.getDefaultGeometryAttribute()
            .geometryAttributeIndex(null /* TODO */)
            .geometryType(
                tmft.getDefaultGeometryDescriptor()
                    .map(TMAttributeDescriptor::getType)
                    .map(TMAttributeType::getValue)
                    .map(TMGeometryType::fromValue)
                    .orElse(null))
            // TODO defaults to true for any TMFeatureType in FeatureSourceHelper#124 and it should
            // be ROLE dependent as well
            .editable(tmft.isWriteable())
            .attributes(
                tmft.getAttributes().stream()
                    .map(
                        a ->
                            new Attribute()
                                .featureType(tmft.getId())
                                .key(a.getName())
                                // Only return generic 'geometry' type for now, frontend doesn't
                                // handle different geometry types. For the default geometry
                                // attribute there is a specific geometry type set
                                .type(
                                    isGeometry(a.getType())
                                        ? TMAttributeType.GEOMETRY
                                        : a.getType())
                                .editable(true /* TODO */)
                                .editAlias(null /* TODO */)
                                .defaultValue(a.getDefaultValue())
                                .nullable(null /* TODO */)
                                .valueList(/*String[]*/ null /* TODO */)
                                .allowValueListOnly(false /* TODO */))
                    .collect(Collectors.toList()));
    return ResponseEntity.ok(r);
  }
}
