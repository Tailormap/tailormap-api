/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller;

import static org.tailormap.api.persistence.helper.TMAttributeTypeHelper.isGeometry;
import static org.tailormap.api.persistence.helper.TMFeatureTypeHelper.getConfiguredAttributes;

import io.micrometer.core.annotation.Timed;
import java.io.Serializable;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import org.tailormap.api.annotation.AppRestController;
import org.tailormap.api.persistence.Application;
import org.tailormap.api.persistence.Form;
import org.tailormap.api.persistence.GeoService;
import org.tailormap.api.persistence.TMFeatureType;
import org.tailormap.api.persistence.helper.TMFeatureTypeHelper;
import org.tailormap.api.persistence.json.AppLayerSettings;
import org.tailormap.api.persistence.json.AppTreeLayerNode;
import org.tailormap.api.persistence.json.GeoServiceLayer;
import org.tailormap.api.persistence.json.TMAttributeDescriptor;
import org.tailormap.api.persistence.json.TMAttributeType;
import org.tailormap.api.persistence.json.TMGeometryType;
import org.tailormap.api.repository.FeatureSourceRepository;
import org.tailormap.api.repository.FormRepository;
import org.tailormap.api.viewer.model.Attribute;
import org.tailormap.api.viewer.model.LayerDetails;
import org.tailormap.api.viewer.model.LayerDetailsForm;

@AppRestController
@Validated
@RequestMapping(
    path = "${tailormap-api.base-path}/{viewerKind}/{viewerName}/layer/{appLayerId}/describe",
    produces = MediaType.APPLICATION_JSON_VALUE)
public class LayerDescriptionController {

  private final FeatureSourceRepository featureSourceRepository;

  private final FormRepository formRepository;

  public LayerDescriptionController(FeatureSourceRepository featureSourceRepository, FormRepository formRepository) {
    this.featureSourceRepository = featureSourceRepository;
    this.formRepository = formRepository;
  }

  @Transactional
  @GetMapping
  @Timed(value = "get_layer_description", description = "Get layer description")
  public ResponseEntity<Serializable> getAppLayerDescription(
      @ModelAttribute Application application,
      @ModelAttribute AppTreeLayerNode appTreeLayerNode,
      @ModelAttribute GeoService service,
      @ModelAttribute GeoServiceLayer layer) {

    if (layer == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Can't find layer " + appTreeLayerNode);
    }

    TMFeatureType tmft = service.findFeatureTypeForLayer(layer, featureSourceRepository);
    if (tmft == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Layer does not have feature type");
    }

    LayerDetails r = new LayerDetails()
        .id(appTreeLayerNode.getId())
        .serviceId(appTreeLayerNode.getServiceId())
        .featureTypeName(tmft.getName())
        .geometryAttribute(tmft.getDefaultGeometryAttribute())
        // TODO deduce from tmft.getDefaultGeometryAttribute()
        .geometryAttributeIndex(null /* TODO */)
        .geometryType(tmft.getDefaultGeometryDescriptor()
            .map(TMAttributeDescriptor::getType)
            .map(TMAttributeType::getValue)
            .map(TMGeometryType::fromValue)
            .orElse(null))
        .editable(TMFeatureTypeHelper.isEditable(application, appTreeLayerNode, tmft));

    AppLayerSettings appLayerSettings = application.getAppLayerSettings(appTreeLayerNode);

    Form form;
    if (appLayerSettings.getFormId() != null) {
      form = formRepository.findById(appLayerSettings.getFormId()).orElse(null);
      if (form != null) {
        r.setForm(new LayerDetailsForm().options(form.getOptions()).fields(form.getFields()));
      }
    }

    Set<String> readOnlyAttributes = TMFeatureTypeHelper.getReadOnlyAttributes(tmft, appLayerSettings);

    getConfiguredAttributes(tmft, appLayerSettings).values().stream()
        .map(pair -> {
          TMAttributeDescriptor a = pair.getLeft();
          return new Attribute()
              .featureType(tmft.getId())
              .key(a.getName())
              // Only return generic 'geometry' type for now, frontend doesn't
              // handle different geometry types. For the default geometry
              // attribute there is a specific geometry type set
              .type(isGeometry(a.getType()) ? TMAttributeType.GEOMETRY : a.getType())
              // primary key can never be edited
              .editable(!a.getName().equals(tmft.getPrimaryKeyAttribute())
                  && !readOnlyAttributes.contains(a.getName()))
              .defaultValue(a.getDefaultValue())
              .nullable(a.getNullable());
        })
        .forEach(r::addAttributesItem);

    return ResponseEntity.ok(r);
  }
}
