/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import io.swagger.v3.oas.annotations.Parameter;

import nl.b3p.tailormap.api.model.Attribute;
import nl.b3p.tailormap.api.model.LayerDetails;
import nl.b3p.tailormap.api.model.RedirectResponse;
import nl.b3p.tailormap.api.repository.ApplicationLayerRepository;
import nl.b3p.tailormap.api.repository.ApplicationRepository;
import nl.b3p.tailormap.api.security.AuthorizationService;
import nl.tailormap.viewer.config.app.Application;
import nl.tailormap.viewer.config.app.ApplicationLayer;
import nl.tailormap.viewer.config.services.Layer;
import nl.tailormap.viewer.config.services.SimpleFeatureType;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.Serializable;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

@RestController
@Validated
@RequestMapping(
        path = "/app/{appId}/layer/{appLayerId}/describe",
        produces = MediaType.APPLICATION_JSON_VALUE)
public class LayerDescriptionController {
    private final ApplicationRepository applicationRepository;
    private final ApplicationLayerRepository applicationLayerRepository;
    private final AuthorizationService authorizationService;

    @PersistenceContext private EntityManager entityManager;

    @Autowired
    public LayerDescriptionController(
            ApplicationRepository applicationRepository,
            ApplicationLayerRepository applicationLayerRepository,
            AuthorizationService authorizationService) {
        this.applicationRepository = applicationRepository;
        this.applicationLayerRepository = applicationLayerRepository;
        this.authorizationService = authorizationService;
    }

    @GetMapping
    public ResponseEntity<Serializable> getAppLayerDescription(
            @Parameter(name = "appId", description = "application id", required = true)
                    @PathVariable("appId")
                    Long appId,
            @Parameter(name = "appLayerId", description = "application layer id", required = true)
                    @PathVariable("appLayerId")
                    Long appLayerId) {
        final Application application = applicationRepository.findById(appId).orElse(null);
        final ApplicationLayer appLayer =
                applicationLayerRepository.findById(appLayerId).orElse(null);
        if (application == null || appLayer == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Requested an application or appLayer that does not exist");
        }

        if (!authorizationService.mayUserRead(appLayer, application)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new RedirectResponse());
        }

        final Layer layer = appLayer.getService().getLayer(appLayer.getLayerName(), entityManager);
        if (layer == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(
                            String.format(
                                    "Can't find layer in service #%d with name \"%s\"",
                                    appLayer.getService().getId(), appLayer.getLayerName()));
        }

        final SimpleFeatureType sft = layer.getFeatureType();
        if (sft == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Layer does not have feature type");
        }

        LayerDetails r = new LayerDetails();
        r.setId(appLayerId);
        r.setServiceId(appLayer.getService().getId());
        r.setFeatureTypeName(sft.getTypeName());
        r.setGeometryAttribute(sft.getGeometryAttribute());
        r.attributes(
                sft.getAttributes().stream()
                        .map(
                                ad -> {
                                    Attribute a = new Attribute();
                                    a.setId(ad.getId());
                                    a.setName(ad.getName());
                                    a.setAlias(ad.getAlias());
                                    a.setType(Attribute.TypeEnum.fromValue(ad.getType()));
                                    return a;
                                })
                        .collect(Collectors.toList()));
        return ResponseEntity.ok(r);
    }
}
