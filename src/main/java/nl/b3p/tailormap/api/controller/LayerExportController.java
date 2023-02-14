/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import static nl.b3p.tailormap.api.MicrometerHelper.tagsToString;
import static nl.b3p.tailormap.api.util.HttpProxyUtil.passthroughResponseHeaders;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import java.io.InputStream;
import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import javax.servlet.http.HttpServletRequest;
import nl.b3p.tailormap.api.MicrometerHelper;
import nl.b3p.tailormap.api.annotation.AppRestController;
import nl.b3p.tailormap.api.geotools.wfs.SimpleWFSHelper;
import nl.b3p.tailormap.api.geotools.wfs.SimpleWFSLayerDescription;
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
import nl.tailormap.viewer.config.services.WMSService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

@AppRestController
@Validated
@RequestMapping(path = "/app/{appId}/layer/{appLayerId}/export/")
public class LayerExportController {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private final MeterRegistry meterRegistry;

  private final LayerRepository layerRepository;

  public LayerExportController(LayerRepository layerRepository, MeterRegistry meterRegistry) {
    this.layerRepository = layerRepository;
    this.meterRegistry = meterRegistry;
  }

  @GetMapping(path = "capabilities")
  @Timed("export_get_capabilities")
  public ResponseEntity<Serializable> capabilities(
      @ModelAttribute Application application, @ModelAttribute ApplicationLayer applicationLayer)
      throws Exception {

    final LayerExportCapabilities capabilities = new LayerExportCapabilities();
    findWFS(
        application,
        applicationLayer,
        params -> {
          if (params.noWFSFound()) {
            capabilities.setOutputFormats(null);
            return null; // ignore
          }
          try {
            List<String> outputFormats =
                meterRegistry
                    .timer(
                        "export_get_capabilities_get_wfs_capabilities", params.getMicrometerTags())
                    .recordCallable(
                        () ->
                            SimpleWFSHelper.getOutputFormats(
                                params.getWfsUrl(),
                                params.getTypeName(),
                                params.getUsername(),
                                params.getPassword()));
            capabilities.setOutputFormats(outputFormats);

          } catch (Exception e) {
            String msg =
                String.format("Error getting capabilities for WFS \"%s\"", params.getWfsUrl());
            if (logger.isTraceEnabled()) {
              logger.trace(msg, e);
            } else {
              logger.warn("{}: {}: {}", msg, e.getClass(), e.getMessage());
            }
            capabilities.setOutputFormats(null);
          }
          return null; // ignore
        });

    capabilities.setExportable(
        capabilities.getOutputFormats() != null && !capabilities.getOutputFormats().isEmpty());
    return ResponseEntity.status(HttpStatus.OK).body(capabilities);
  }

  @RequestMapping(
      path = "download",
      method = {RequestMethod.GET, RequestMethod.POST})
  public ResponseEntity<?> download(
      @ModelAttribute Application application,
      @ModelAttribute ApplicationLayer applicationLayer,
      @RequestParam String outputFormat,
      @RequestParam(required = false) List<String> attributes,
      @RequestParam(required = false) String filter,
      @RequestParam(required = false) String sortBy,
      @RequestParam(required = false) String sortOrder,
      @RequestParam(required = false) String crs,
      HttpServletRequest request)
      throws Exception {

    return (ResponseEntity<?>)
        findWFS(
            application,
            applicationLayer,
            params -> {
              if (params.noWFSFound()) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("No suitable WFS available for layer export");
              } else {
                return downloadFromWFS(
                    params, outputFormat, attributes, filter, sortBy, sortOrder, crs, request);
              }
            });
  }

  private ResponseEntity<?> downloadFromWFS(
      WFSSearchResultParams params,
      String outputFormat,
      List<String> attributes,
      String filter,
      String sortBy,
      String sortOrder,
      String crs,
      HttpServletRequest request) {

    Tags tags = params.getMicrometerTags().and(Tag.of("format", outputFormat));

    MultiValueMap<String, String> getFeatureParameters = new LinkedMultiValueMap<>();
    // A layer could have more than one featureType as source, currently we assume it's just one
    getFeatureParameters.add("typeNames", params.getTypeName());
    getFeatureParameters.add("outputFormat", outputFormat);
    if (filter != null) {
      // GeoServer vendor-specific
      // https://docs.geoserver.org/latest/en/user/services/wfs/vendor.html#cql-filters
      getFeatureParameters.add("cql_filter", filter);
    }
    if (crs != null) {
      getFeatureParameters.add("srsName", crs);
    }
    if (attributes != null && !attributes.isEmpty()) {

      // If the WFS was discovered by a WMS DescribeLayer, we haven't loaded the entire
      // feature type XML schema (because this can be very slow and error-prone) and we don't
      // know the name of the geometry attribute so do not specify the propertyNames parameter
      // to include all propertyNames.
      // If the geometry attribute is known, add it to the propertyNames otherwise the result
      // won't have geometries.
      if (params.getGeometryAttribute() != null) {
        attributes.add(params.getGeometryAttribute());
        getFeatureParameters.add("propertyName", String.join(",", attributes));
      }
    }
    if (sortBy != null) {
      getFeatureParameters.add("sortBy", sortBy + ("asc".equals(sortOrder) ? " A" : " D"));
    }
    URI wfsGetFeature =
        SimpleWFSHelper.getWFSRequestURL(params.getWfsUrl(), "GetFeature", getFeatureParameters);

    logger.info(
        "Layer download {}, proxying WFS GetFeature request {}", tagsToString(tags), wfsGetFeature);

    try {
      // TODO: close JPA connection before proxying

      HttpResponse<InputStream> response =
          meterRegistry
              .timer("export_download_first_response", tags)
              .recordCallable(
                  () ->
                      WFSProxy.proxyWfsRequest(
                          wfsGetFeature, params.getUsername(), params.getPassword(), request));

      meterRegistry
          .counter(
              "export_download_response", tags.and("response_status", response.statusCode() + ""))
          .increment();

      logger.info(
          "Layer download response code: {}, content type: {}, disposition: {}",
          response.statusCode(),
          response.headers().firstValue("Content-Type").map(Object::toString).orElse("<none>"),
          response
              .headers()
              .firstValue("Content-Disposition")
              .map(Object::toString)
              .orElse("<none>"));

      InputStreamResource body = new InputStreamResource(response.body());

      org.springframework.http.HttpHeaders headers =
          passthroughResponseHeaders(
              response.headers(), Set.of("Content-Type", "Content-Disposition"));

      // TODO: micrometer record response size and time
      return ResponseEntity.status(response.statusCode()).headers(headers).body(body);
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("Bad Gateway");
    }
  }

  private static class WFSSearchResultParams {
    private final String wfsUrl;
    private final String typeName;
    private final String geometryAttribute;
    private final String username;
    private final String password;
    private final Tags micrometerTags;

    public WFSSearchResultParams(
        String wfsUrl,
        String typeName,
        String geometryAttribute,
        String username,
        String password,
        Tags micrometerTags) {
      this.wfsUrl = wfsUrl;
      this.typeName = typeName;
      this.geometryAttribute = geometryAttribute;
      this.username = username;
      this.password = password;
      this.micrometerTags = micrometerTags;
    }

    // <editor-fold desc="getters">
    public String getWfsUrl() {
      return wfsUrl;
    }

    public String getTypeName() {
      return typeName;
    }

    public String getGeometryAttribute() {
      return geometryAttribute;
    }

    public String getUsername() {
      return username;
    }

    public String getPassword() {
      return password;
    }

    public Tags getMicrometerTags() {
      return micrometerTags;
    }

    public boolean noWFSFound() {
      return wfsUrl == null || typeName == null;
    }
    // </editor-fold>
  }

  private Object findWFS(
      Application application,
      ApplicationLayer applicationLayer,
      Function<WFSSearchResultParams, ?> function)
      throws Exception {

    final GeoService service = applicationLayer.getService();
    final Layer serviceLayer =
        this.layerRepository.getByServiceAndName(service, applicationLayer.getLayerName());

    SimpleFeatureType featureType = serviceLayer.getFeatureType();

    Tags tags =
        MicrometerHelper.getTags(application, applicationLayer, service, serviceLayer, featureType);

    String wfsUrl = null;
    String typeName = null;
    String username = null;
    String password = null;
    String geometryAttribute = null;

    if (featureType != null) {
      FeatureSource featureSource = featureType.getFeatureSource();

      if (featureSource instanceof WFSFeatureSource) {
        wfsUrl = featureSource.getUrl();
        typeName = featureType.getTypeName();
        username = featureSource.getUsername();
        password = featureSource.getPassword();
        geometryAttribute = featureType.getGeometryAttribute();
      }
    }

    if ((wfsUrl == null || typeName == null) && service instanceof WMSService) {
      // Try to find out the WFS by doing a DescribeLayer request (from OGC SLD spec)
      WMSService wmsService = (WMSService) service;
      username = wmsService.getUsername();
      password = wmsService.getPassword();

      SimpleWFSLayerDescription wfsLayerDescription =
          getWFSLayerDescriptionForWMS(wmsService, serviceLayer, tags);
      if (wfsLayerDescription != null) {
        tags =
            tags.and("featureSourceUrl", wfsLayerDescription.getWfsUrl())
                .and("featureTypeName", wfsLayerDescription.getFirstTypeName());

        wfsUrl = wfsLayerDescription.getWfsUrl();
        typeName = wfsLayerDescription.getFirstTypeName();
      }
    }

    if (wfsUrl != null && typeName != null) {
      tags = tags.and("featureSourceUrl", wfsUrl).and("featureTypeName", typeName);
    }
    return function.apply(
        new WFSSearchResultParams(wfsUrl, typeName, geometryAttribute, username, password, tags));
  }

  private SimpleWFSLayerDescription getWFSLayerDescriptionForWMS(
      WMSService wmsService, Layer serviceLayer, Tags tags) throws Exception {
    SimpleWFSLayerDescription wfsLayerDescription =
        meterRegistry
            .timer("export_get_capabilities_wms_describelayer", tags)
            .recordCallable(
                () ->
                    SimpleWFSHelper.describeWMSLayer(
                        wmsService.getUrl(),
                        wmsService.getUsername(),
                        wmsService.getPassword(),
                        List.of(serviceLayer.getName())));
    if (wfsLayerDescription != null && wfsLayerDescription.getTypeNames().length > 0) {
      logger.info(
          "WMS described layer \"{}\" with typeNames \"{}\" of WFS \"{}\" for WMS \"{}\"",
          serviceLayer.getName(),
          Arrays.toString(wfsLayerDescription.getTypeNames()),
          wfsLayerDescription.getWfsUrl(),
          wmsService.getUrl());

      return wfsLayerDescription;
    }
    return null;
  }
}
