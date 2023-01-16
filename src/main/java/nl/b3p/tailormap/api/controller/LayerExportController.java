/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import nl.b3p.tailormap.api.annotation.AppRestController;
import nl.b3p.tailormap.api.geotools.wfs.SimpleWFSHelper;
import nl.b3p.tailormap.api.model.LayerExportCapabilities;
import nl.b3p.tailormap.api.repository.LayerRepository;
import nl.tailormap.viewer.config.app.Application;
import nl.tailormap.viewer.config.app.ApplicationLayer;
import nl.tailormap.viewer.config.services.FeatureSource;
import nl.tailormap.viewer.config.services.GeoService;
import nl.tailormap.viewer.config.services.Layer;
import nl.tailormap.viewer.config.services.SimpleFeatureType;
import nl.tailormap.viewer.config.services.WFSFeatureSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.Serializable;
import java.util.Collections;

@AppRestController
@Validated
@RequestMapping(path = "/app/{appId}/layer/{appLayerId}/export/")
public class LayerExportController {

    private final LayerRepository layerRepository;

    public LayerExportController(LayerRepository layerRepository) {
        this.layerRepository = layerRepository;
    }

    @GetMapping(path = "capabilities")
    public ResponseEntity<Serializable> capabilities(
            @ModelAttribute Application application,
            @ModelAttribute ApplicationLayer applicationLayer)
            throws Exception {
        LayerExportCapabilities capabilities = new LayerExportCapabilities();
        capabilities.setExportable(false);
        capabilities.setOutputFormats(Collections.emptyList());

        // TODO move to injectable service

        final GeoService service = applicationLayer.getService();
        final Layer serviceLayer =
                this.layerRepository.getByServiceAndName(service, applicationLayer.getLayerName());

        SimpleFeatureType featureType = serviceLayer.getFeatureType();

        if (featureType != null) {
            FeatureSource featureSource = featureType.getFeatureSource();

            if (featureSource instanceof WFSFeatureSource) {
                String wfsUrl = featureSource.getUrl();
                String typeName = featureType.getTypeName();
                String username = featureSource.getUsername();
                String password = featureSource.getPassword();

                capabilities.setOutputFormats(SimpleWFSHelper.getOutputFormats(wfsUrl, typeName, username, password));
                capabilities.setExportable(true);
            }

            // TODO: If Layer is from WFS service, do DescribeLayer request
        }

        return ResponseEntity.status(HttpStatus.OK).body(capabilities);
    }
}
