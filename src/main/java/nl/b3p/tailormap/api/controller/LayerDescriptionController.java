/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import nl.b3p.tailormap.api.annotation.AppRestController;
import nl.b3p.tailormap.api.model.Attribute;
import nl.b3p.tailormap.api.model.LayerDetails;
import nl.tailormap.viewer.config.app.Application;
import nl.tailormap.viewer.config.app.ApplicationLayer;
import nl.tailormap.viewer.config.app.ConfiguredAttribute;
import nl.tailormap.viewer.config.services.AttributeDescriptor;
import nl.tailormap.viewer.config.services.Layer;
import nl.tailormap.viewer.config.services.SimpleFeatureType;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.Serializable;
import java.util.List;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.validation.constraints.NotNull;

@AppRestController
@Validated
@RequestMapping(
        path = "/app/{appId}/layer/{appLayerId}/describe",
        produces = MediaType.APPLICATION_JSON_VALUE)
public class LayerDescriptionController {

    @PersistenceContext private EntityManager entityManager;

    @GetMapping
    public ResponseEntity<Serializable> getAppLayerDescription(
            @ModelAttribute Application application,
            @ModelAttribute ApplicationLayer applicationLayer) {
        final Layer layer =
                applicationLayer
                        .getService()
                        .getLayer(applicationLayer.getLayerName(), entityManager);
        if (layer == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(
                            String.format(
                                    "Can't find layer in service #%d with name \"%s\"",
                                    applicationLayer.getService().getId(),
                                    applicationLayer.getLayerName()));
        }

        final SimpleFeatureType sft = layer.getFeatureType();
        if (sft == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Layer does not have feature type");
        }

        LayerDetails r = new LayerDetails();
        r.setId(applicationLayer.getId());
        r.setServiceId(applicationLayer.getService().getId());
        r.setFeatureTypeName(sft.getTypeName());
        r.setGeometryAttribute(sft.getGeometryAttribute());
        r.attributes(
                getVisibleAttributes(applicationLayer, sft).stream()
                        .map(
                                ca -> {
                                    AttributeDescriptor ad =
                                            sft.getAttribute(ca.getAttributeName());
                                    Attribute a = new Attribute();
                                    // ca or ad? Not used by frontend for anything
                                    a.setId(ca.getId());
                                    a.setName(ad.getName());
                                    a.setAlias(ad.getAlias());
                                    a.setEditAlias(ca.getEditAlias());
                                    // TODO: set more attributes from ca
                                    a.setType(Attribute.TypeEnum.fromValue(ad.getType()));
                                    return a;
                                })
                        .collect(Collectors.toList()));
        return ResponseEntity.ok(r);
    }

    private List<ConfiguredAttribute> getVisibleAttributes(
            @NotNull ApplicationLayer appLayer, @NotNull SimpleFeatureType sft) {
        List<ConfiguredAttribute> configuredAttributes = appLayer.getAttributes(sft);
        return configuredAttributes.stream()
                .filter(ConfiguredAttribute::isVisible)
                .collect(Collectors.toList());
    }
}
