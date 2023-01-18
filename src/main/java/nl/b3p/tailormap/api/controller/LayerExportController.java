/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import nl.b3p.tailormap.api.MicrometerHelper;
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
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
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

import javax.servlet.http.HttpServletRequest;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URI;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static nl.b3p.tailormap.api.MicrometerHelper.tagsToString;
import static nl.b3p.tailormap.api.util.HttpProxyUtil.passthroughResponseHeaders;

@AppRestController
@Validated
@RequestMapping(path = "/app/{appId}/layer/{appLayerId}/export/")
public class LayerExportController {
    private static final Log logger = LogFactory.getLog(LayerExportController.class);
    private final MeterRegistry meterRegistry;

    private final LayerRepository layerRepository;

    public LayerExportController(LayerRepository layerRepository, MeterRegistry meterRegistry) {
        this.layerRepository = layerRepository;
        this.meterRegistry = meterRegistry;
    }

    @GetMapping(path = "capabilities")
    @Timed("export_get_capabilities")
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

        Tags tags = MicrometerHelper.getTags(application, applicationLayer, service, serviceLayer, featureType);

        if (featureType != null) {
            FeatureSource featureSource = featureType.getFeatureSource();

            if (featureSource instanceof WFSFeatureSource) {
                String wfsUrl = featureSource.getUrl();
                String typeName = featureType.getTypeName();
                String username = featureSource.getUsername();
                String password = featureSource.getPassword();

                List<String> outputFormats = meterRegistry.timer(
                        "export_get_capabilities_direct_wfs_source",
                        tags
                ).recordCallable(() -> SimpleWFSHelper.getOutputFormats(wfsUrl, typeName, username, password));

                capabilities.setOutputFormats(outputFormats);
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
            @RequestParam(required = false) List<String> attributes,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder,
            @RequestParam(required = false) String crs,
            HttpServletRequest request) {

        final GeoService service = applicationLayer.getService();
        final Layer serviceLayer =
                this.layerRepository.getByServiceAndName(service, applicationLayer.getLayerName());

        SimpleFeatureType featureType = serviceLayer.getFeatureType();

        Tags tags = MicrometerHelper.getTags(application, applicationLayer, service, serviceLayer, featureType);

        if (featureType != null) {
            FeatureSource featureSource = featureType.getFeatureSource();

            if (featureSource instanceof WFSFeatureSource) {
                String wfsUrl = featureSource.getUrl();
                String typeName = featureType.getTypeName();
                String username = featureSource.getUsername();
                String password = featureSource.getPassword();

                tags = tags.and(
                        Tag.of("format", outputFormat)
                );

                MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
                // A layer could have more than one featureType as source, currently we assume it's just one
                parameters.add("typeNames", typeName);
                parameters.add("outputFormat", outputFormat);
                if (filter != null) {
                    // GeoServer vendor-specific
                    // https://docs.geoserver.org/latest/en/user/services/wfs/vendor.html#cql-filters
                    parameters.add("cql_filter", filter);
                }
                if (crs != null) {
                    parameters.add("srsName", crs);
                }
                if (attributes != null && !attributes.isEmpty()) {
                    // If we don't do this, the output won't have geometries
                    attributes.add(featureType.getGeometryAttribute());
                    parameters.add("propertyName", String.join(",", attributes));
                }
                if (sortBy != null) {
                    parameters.add("sortBy", sortBy + ("asc".equals(sortOrder) ? " A" : " D"));
                }
                URI wfsGetFeature =
                        SimpleWFSHelper.getWFSRequestURL(wfsUrl, "GetFeature", parameters);

                logger.info(String.format("Layer download %s, proxying WFS GetFeature request %s", tagsToString(tags), wfsGetFeature));

                try {
                    // TODO: close JPA connection before proxying

                    HttpResponse<InputStream> response = meterRegistry
                            .timer("export_download_first_response", tags)
                            .recordCallable(() -> WFSProxy.proxyWfsRequest(wfsGetFeature, username, password, request));

                    meterRegistry.counter("export_download_response",
                            tags.and("response_status", response.statusCode() + ""))
                            .increment();

                    logger.info(String.format("Layer download response code: %s, content type: %s",
                            response.statusCode(),
                            response.headers().firstValue("Content-Type").map(Object::toString)));

                    InputStreamResource body = new InputStreamResource(response.body());

                    org.springframework.http.HttpHeaders headers =
                            passthroughResponseHeaders(
                                    response.headers(),
                                    Set.of("Content-Type", "Content-Disposition"));

                    // TODO: micrometer record response size and time
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
