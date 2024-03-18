/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import static nl.b3p.tailormap.api.persistence.helper.TMFeatureTypeHelper.getConfiguredAttributes;
import static nl.b3p.tailormap.api.util.HttpProxyUtil.passthroughResponseHeaders;

import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import jakarta.servlet.http.HttpServletRequest;
import java.io.InputStream;
import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import nl.b3p.tailormap.api.annotation.AppRestController;
import nl.b3p.tailormap.api.geotools.wfs.SimpleWFSHelper;
import nl.b3p.tailormap.api.geotools.wfs.SimpleWFSLayerDescription;
import nl.b3p.tailormap.api.geotools.wfs.WFSProxy;
import nl.b3p.tailormap.api.persistence.Application;
import nl.b3p.tailormap.api.persistence.GeoService;
import nl.b3p.tailormap.api.persistence.TMFeatureSource;
import nl.b3p.tailormap.api.persistence.TMFeatureType;
import nl.b3p.tailormap.api.persistence.json.AppLayerSettings;
import nl.b3p.tailormap.api.persistence.json.AppTreeLayerNode;
import nl.b3p.tailormap.api.persistence.json.GeoServiceLayer;
import nl.b3p.tailormap.api.persistence.json.GeoServiceProtocol;
import nl.b3p.tailormap.api.persistence.json.ServiceAuthentication;
import nl.b3p.tailormap.api.repository.FeatureSourceRepository;
import nl.b3p.tailormap.api.viewer.model.LayerExportCapabilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@AppRestController
@RequestMapping(
    path = "${tailormap-api.base-path}/{viewerKind}/{viewerName}/layer/{appLayerId}/export/")
public class LayerExportController {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final FeatureSourceRepository featureSourceRepository;

  public LayerExportController(FeatureSourceRepository featureSourceRepository) {
    this.featureSourceRepository = featureSourceRepository;
  }

  @Transactional
  @GetMapping(path = "capabilities")
  @Timed(value = "export_get_capabilities", description = "Get layer export capabilities")
  public ResponseEntity<Serializable> capabilities(
      @ModelAttribute GeoService service, @ModelAttribute GeoServiceLayer layer) throws Exception {

    final LayerExportCapabilities capabilities = new LayerExportCapabilities();

    TMFeatureType tmft = service.findFeatureTypeForLayer(layer, featureSourceRepository);
    WFSSearchResult wfsSearchResult = findWFSFeatureType(service, layer, tmft);

    if (!wfsSearchResult.found()) {
      capabilities.setOutputFormats(null);
    } else {
      try {
        List<String> outputFormats =
            SimpleWFSHelper.getOutputFormats(
                wfsSearchResult.wfsUrl(),
                wfsSearchResult.typeName(),
                wfsSearchResult.username(),
                wfsSearchResult.password());
        capabilities.setOutputFormats(outputFormats);
      } catch (Exception e) {
        String msg =
            String.format("Error getting capabilities for WFS \"%s\"", wfsSearchResult.wfsUrl());
        if (logger.isTraceEnabled()) {
          logger.trace(msg, e);
        } else {
          logger.warn("{}: {}: {}", msg, e.getClass(), e.getMessage());
        }
        capabilities.setOutputFormats(null);
      }
    }

    capabilities.setExportable(
        capabilities.getOutputFormats() != null && !capabilities.getOutputFormats().isEmpty());
    return ResponseEntity.status(HttpStatus.OK).body(capabilities);
  }

  @Transactional
  @RequestMapping(
      path = "download",
      method = {RequestMethod.GET, RequestMethod.POST})
  @Counted(value = "export_download", description = "Count of layer downloads")
  public ResponseEntity<?> download(
      @ModelAttribute GeoService service,
      @ModelAttribute GeoServiceLayer layer,
      @ModelAttribute Application application,
      @ModelAttribute AppTreeLayerNode appTreeLayerNode,
      @RequestParam String outputFormat,
      @RequestParam(required = false) List<String> attributes,
      @RequestParam(required = false) String filter,
      @RequestParam(required = false) String sortBy,
      @RequestParam(required = false) String sortOrder,
      @RequestParam(required = false) String crs,
      HttpServletRequest request)
      throws Exception {

    TMFeatureType tmft = service.findFeatureTypeForLayer(layer, featureSourceRepository);
    AppLayerSettings appLayerSettings = application.getAppLayerSettings(appTreeLayerNode);
    WFSSearchResult wfsSearchResult = findWFSFeatureType(service, layer, tmft);

    if (!wfsSearchResult.found()) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "No suitable WFS available for layer export");
    } else {
      // Get attributes in configured or original order
      Set<String> nonHiddenAttributes = getConfiguredAttributes(tmft, appLayerSettings).keySet();

      if (attributes == null) {
        attributes = Collections.emptyList();
      }
      if (!attributes.isEmpty()) {
        // Try to only export non-hidden property names. Note that hiding attributes is not a
        // security feature. TM does not try to hide the original WMS URL (even when proxying).
        // Secret attributes should not be exposed by the WFS service discoverable through the WMS
        // because all property names are exported when we don't know the default geometry property
        // name. But a user can also do a DescribeLayer request himself on the original WMS and do a
        // GetFeature request to get all attributes.
        if (!nonHiddenAttributes.containsAll(attributes)) {
          throw new ResponseStatusException(
              HttpStatus.BAD_REQUEST,
              "One or more requested attributes are not available on the feature type");
        }
      } else if (!tmft.getSettings().getHideAttributes().isEmpty()) {
        // Only specify specific propNames if there are hidden attributes. Having no propNames
        // request parameter to request all propNames is less error-prone than specifiying the ones
        // we have saved in the feature type
        attributes = new ArrayList<>(nonHiddenAttributes);
      }

      return downloadFromWFS(
          wfsSearchResult, outputFormat, attributes, filter, sortBy, sortOrder, crs, request);
    }
  }

  private ResponseEntity<?> downloadFromWFS(
      WFSSearchResult wfsSearchResult,
      String outputFormat,
      List<String> attributes,
      String filter,
      String sortBy,
      String sortOrder,
      String crs,
      HttpServletRequest request) {

    MultiValueMap<String, String> getFeatureParameters = new LinkedMultiValueMap<>();
    // A layer could have more than one featureType as source, currently we assume it's just one
    getFeatureParameters.add("typeNames", wfsSearchResult.typeName());
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

      // If the WFS was discovered by a WMS DescribeLayer, we haven't loaded the entire feature type
      // XML schema (because this can be very slow and error-prone) and we don't know the name of
      // the geometry attribute so do not specify the propertyNames parameter to include all
      // propertyNames. If the geometry attribute is known, add it to the propertyNames otherwise
      // the result won't have geometries.
      if (wfsSearchResult.geometryAttribute() != null) {
        attributes.add(wfsSearchResult.geometryAttribute());
        getFeatureParameters.add("propertyName", String.join(",", attributes));
      }
    }
    if (sortBy != null) {
      getFeatureParameters.add("sortBy", sortBy + ("asc".equals(sortOrder) ? " A" : " D"));
    }
    URI wfsGetFeature =
        SimpleWFSHelper.getWFSRequestURL(
            wfsSearchResult.wfsUrl(), "GetFeature", getFeatureParameters);

    logger.info(
        "Layer download {}, proxying WFS GetFeature request {}",
        null /*tagsToString(tags)*/,
        wfsGetFeature);

    try {
      // TODO: close JPA connection before proxying

      HttpResponse<InputStream> response =
          WFSProxy.proxyWfsRequest(
              wfsGetFeature, wfsSearchResult.username(), wfsSearchResult.password(), request);

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

      // TODO: record response size and time with micrometer
      return ResponseEntity.status(response.statusCode()).headers(headers).body(body);
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("Bad Gateway");
    }
  }

  private record WFSSearchResult(
      String wfsUrl, String typeName, String geometryAttribute, String username, String password) {

    public boolean found() {
      return wfsUrl != null && typeName != null;
    }
    // </editor-fold>
  }

  private WFSSearchResult findWFSFeatureType(
      GeoService service, GeoServiceLayer layer, TMFeatureType tmft) {

    String wfsUrl = null;
    String typeName = null;
    String username = null;
    String password = null;
    String geometryAttribute = null;
    ServiceAuthentication auth = null;

    if (tmft != null) {
      TMFeatureSource featureSource = tmft.getFeatureSource();

      if (featureSource.getProtocol() == TMFeatureSource.Protocol.WFS) {
        wfsUrl = featureSource.getUrl();
        typeName = tmft.getName();
        auth = featureSource.getAuthentication();
        geometryAttribute = tmft.getDefaultGeometryAttribute();
      }
    }

    if ((wfsUrl == null || typeName == null) && service.getProtocol() == GeoServiceProtocol.WMS) {
      // Try to find out the WFS by doing a DescribeLayer request (from OGC SLD spec)
      auth = service.getAuthentication();

      SimpleWFSLayerDescription wfsLayerDescription =
          getWFSLayerDescriptionForWMS(service, layer.getName() /*, tags*/);
      if (wfsLayerDescription != null) {
        wfsUrl = wfsLayerDescription.getWfsUrl();
        typeName = wfsLayerDescription.getFirstTypeName();
        auth = service.getAuthentication();
      }
    }

    if (auth != null && auth.getMethod() == ServiceAuthentication.MethodEnum.PASSWORD) {
      username = auth.getUsername();
      password = auth.getPassword();
    }
    return new WFSSearchResult(wfsUrl, typeName, geometryAttribute, username, password /*, tags*/);
  }

  private SimpleWFSLayerDescription getWFSLayerDescriptionForWMS(
      GeoService wmsService, String layerName /*, Tags tags*/) {
    String username = null;
    String password = null;
    if (wmsService.getAuthentication() != null
        && wmsService.getAuthentication().getMethod()
            == ServiceAuthentication.MethodEnum.PASSWORD) {
      username = wmsService.getAuthentication().getUsername();
      password = wmsService.getAuthentication().getPassword();
    }
    SimpleWFSLayerDescription wfsLayerDescription =
        SimpleWFSHelper.describeWMSLayer(wmsService.getUrl(), username, password, layerName);
    if (wfsLayerDescription != null && wfsLayerDescription.getTypeNames().length > 0) {
      logger.info(
          "WMS described layer \"{}\" with typeNames \"{}\" of WFS \"{}\" for WMS \"{}\"",
          layerName,
          Arrays.toString(wfsLayerDescription.getTypeNames()),
          wfsLayerDescription.getWfsUrl(),
          wmsService.getUrl());

      return wfsLayerDescription;
    }
    return null;
  }
}
