/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import static nl.b3p.tailormap.api.util.HttpProxyUtil.passthroughResponseHeaders;

import nl.b3p.tailormap.api.annotation.AppRestController;
import nl.b3p.tailormap.api.geotools.wfs.SimpleWFSHelper;
import nl.b3p.tailormap.api.geotools.wfs.WFSProxy;
import nl.b3p.tailormap.api.model.LayerExportCapabilities;
import nl.b3p.tailormap.api.repository.LayerRepository;
import nl.tailormap.viewer.config.app.Application;
import nl.tailormap.viewer.config.app.ApplicationLayer;
import nl.tailormap.viewer.config.services.FeatureSource;
import nl.tailormap.viewer.config.services.GeoService;
import nl.tailormap.viewer.config.services.Layer;
import nl.tailormap.viewer.config.services.SimpleFeatureType;
import nl.tailormap.viewer.config.services.WFSFeatureSource;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.InputStream;
import java.io.Serializable;
import java.net.URI;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

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

                capabilities.setOutputFormats(
                        SimpleWFSHelper.getOutputFormats(wfsUrl, typeName, username, password));
                capabilities.setExportable(true);
                return ResponseEntity.status(HttpStatus.OK).body(capabilities);
            }

            // TODO: If Layer is from WFS service, do DescribeLayer request
        }

        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body("Not implemented");
    }

    @GetMapping(path = "download")
    public ResponseEntity<?> download(
            @ModelAttribute Application application,
            @ModelAttribute ApplicationLayer applicationLayer,
            @RequestParam String outputFormat,
            /*            @RequestParam List<String> attributes,
            @RequestParam String filter,
            @RequestParam String sortBy,
            @RequestParam String sortOrder*/
            HttpServletRequest request) {

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

                MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
                parameters.add("TYPENAME", typeName);
                parameters.add("OUTPUTFORMAT", outputFormat);
                URI wfsGetFeature =
                        SimpleWFSHelper.getWFSRequestURL(wfsUrl, "GetFeature", parameters);

                try {
                    // TODO: close JPA connection before proxying
                    HttpResponse<InputStream> response =
                            WFSProxy.proxyWfsRequest(wfsGetFeature, username, password, request);

                    InputStreamResource body = new InputStreamResource(response.body());

                    org.springframework.http.HttpHeaders headers =
                            passthroughResponseHeaders(
                                    response.headers(),
                                    Set.of("Content-Type", "Content-Disposition"));

                    return ResponseEntity.status(response.statusCode()).headers(headers).body(body);
                } catch (Exception e) {
                    return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("Bad Gateway");
                }
            }

            // TODO: If Layer is from WFS service, do DescribeLayer request
        }

        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body("Not implemented");
    }
}
