/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller;

import static org.tailormap.api.persistence.helper.TMFeatureTypeHelper.getConfiguredAttributes;
import static org.tailormap.api.util.HttpProxyUtil.passthroughResponseHeaders;

import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.geotools.api.feature.type.AttributeDescriptor;
import org.geotools.data.wfs.WFSDataStore;
import org.geotools.data.wfs.WFSDataStoreFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.tailormap.api.annotation.AppRestController;
import org.tailormap.api.geotools.wfs.SimpleWFSHelper;
import org.tailormap.api.geotools.wfs.SimpleWFSLayerDescription;
import org.tailormap.api.geotools.wfs.WFSProxy;
import org.tailormap.api.persistence.Application;
import org.tailormap.api.persistence.GeoService;
import org.tailormap.api.persistence.TMFeatureSource;
import org.tailormap.api.persistence.TMFeatureType;
import org.tailormap.api.persistence.json.AppLayerSettings;
import org.tailormap.api.persistence.json.AppTreeLayerNode;
import org.tailormap.api.persistence.json.GeoServiceLayer;
import org.tailormap.api.persistence.json.GeoServiceProtocol;
import org.tailormap.api.persistence.json.ServiceAuthentication;
import org.tailormap.api.repository.FeatureSourceRepository;
import org.tailormap.api.viewer.model.LayerExportCapabilities;

@AppRestController
@RequestMapping(
    path = "${tailormap-api.base-path}/{viewerKind}/{viewerName}/layer/{appLayerId}/export/")
public class LayerExportController {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Value("#{'${tailormap-api.export.allowed-outputformats}'.split(',')}")
  private List<String> allowedOutputFormats;

  private final FeatureSourceRepository featureSourceRepository;

  public LayerExportController(FeatureSourceRepository featureSourceRepository) {
    this.featureSourceRepository = featureSourceRepository;
  }

  @Transactional
  @GetMapping(path = "capabilities")
  @Timed(value = "export_get_capabilities", description = "Get layer export capabilities")
  public ResponseEntity<Serializable> capabilities(
      @ModelAttribute GeoService service, @ModelAttribute GeoServiceLayer layer) {

    final LayerExportCapabilities capabilities = new LayerExportCapabilities().exportable(false);

    TMFeatureType tmft = service.findFeatureTypeForLayer(layer, featureSourceRepository);

    if (tmft != null) {
      WFSTypeNameDescriptor wfsTypeNameDescriptor = findWFSFeatureType(service, layer, tmft);

      if (wfsTypeNameDescriptor != null) {
        try {
          List<String> outputFormats =
              SimpleWFSHelper.getOutputFormats(
                  wfsTypeNameDescriptor.wfsUrl(),
                  wfsTypeNameDescriptor.typeName(),
                  wfsTypeNameDescriptor.username(),
                  wfsTypeNameDescriptor.password());
          capabilities.setOutputFormats(outputFormats);
        } catch (Exception e) {
          String msg =
              String.format(
                  "Error getting capabilities for WFS \"%s\"", wfsTypeNameDescriptor.wfsUrl());
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
    }

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
      HttpServletRequest request) {

    // Validate outputFormat
    if (!allowedOutputFormats.contains(outputFormat)) {
      logger.warn("Invalid output format requested: {}", outputFormat);
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid output format");
    }

    TMFeatureType tmft = service.findFeatureTypeForLayer(layer, featureSourceRepository);
    AppLayerSettings appLayerSettings = application.getAppLayerSettings(appTreeLayerNode);

    if (tmft == null) {
      logger.debug("Layer export requested for layer without feature type");
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    // Find a WFS feature type either because it is configured in Tailormap or by a SLD
    // DescribeLayer request
    WFSTypeNameDescriptor wfsTypeNameDescriptor = findWFSFeatureType(service, layer, tmft);

    if (wfsTypeNameDescriptor == null) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "No suitable WFS available for layer export");
    }

    if (attributes == null) {
      attributes = new ArrayList<>();
    }

    // Get attributes in configured or original order
    Set<String> nonHiddenAttributes = getConfiguredAttributes(tmft, appLayerSettings).keySet();

    if (!attributes.isEmpty()) {
      // Only export non-hidden property names
      if (!nonHiddenAttributes.containsAll(attributes)) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "One or more requested attributes are not available on the feature type");
      }
    } else if (!tmft.getSettings().getHideAttributes().isEmpty()) {
      // Only specify specific propNames if there are hidden attributes. Having no propNames
      // request parameter to request all propNames is less error-prone than specifying the ones
      // we have saved in the feature type
      attributes = new ArrayList<>(nonHiddenAttributes);
    }

    // Empty attributes means we won't specify propNames in GetFeature request, but if we do select
    // only some property names we need the geometry attribute which is not in the 'attributes'
    // request param so spatial export formats don't have the geometry missing.
    if (!attributes.isEmpty() && tmft.getDefaultGeometryAttribute() != null) {
      attributes.add(tmft.getDefaultGeometryAttribute());
    }

    // Remove attributes which the WFS does not expose. This can be the case when using the
    // 'customize attributes' feature in GeoServer but when TM has been configured with a JDBC
    // feature type with all the attributes. Requesting a non-existing attribute will return an
    // error.
    try {
      List<String> wfsAttributeNames = getWFSAttributeNames(wfsTypeNameDescriptor);
      attributes.retainAll(wfsAttributeNames);
    } catch (IOException e) {
      logger.error("Error getting WFS feature type", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body("Error getting WFS feature type");
    }

    return downloadFromWFS(
        wfsTypeNameDescriptor, outputFormat, attributes, filter, sortBy, sortOrder, crs, request);
  }

  private ResponseEntity<?> downloadFromWFS(
      WFSTypeNameDescriptor wfsTypeName,
      String outputFormat,
      List<String> attributes,
      String filter,
      String sortBy,
      String sortOrder,
      String crs,
      HttpServletRequest request) {

    MultiValueMap<String, String> getFeatureParameters = new LinkedMultiValueMap<>();
    // A layer could have more than one featureType as source, currently we assume it's just one
    getFeatureParameters.add("typeNames", wfsTypeName.typeName());
    getFeatureParameters.add("outputFormat", outputFormat);
    if (filter != null) {
      // GeoServer vendor-specific
      // https://docs.geoserver.org/latest/en/user/services/wfs/vendor.html#cql-filters
      getFeatureParameters.add("cql_filter", filter);
    }
    if (crs != null) {
      getFeatureParameters.add("srsName", crs);
    }
    if (!CollectionUtils.isEmpty(attributes)) {
      getFeatureParameters.add("propertyName", String.join(",", attributes));
    }
    if (sortBy != null) {
      getFeatureParameters.add("sortBy", sortBy + ("asc".equals(sortOrder) ? " A" : " D"));
    }
    URI wfsGetFeature =
        SimpleWFSHelper.getWFSRequestURL(wfsTypeName.wfsUrl(), "GetFeature", getFeatureParameters);

    logger.info("Layer download, proxying WFS GetFeature request {}", wfsGetFeature);
    try {
      // TODO: close JPA connection before proxying
      HttpResponse<InputStream> response =
          WFSProxy.proxyWfsRequest(
              wfsGetFeature, wfsTypeName.username(), wfsTypeName.password(), request);

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

  private record WFSTypeNameDescriptor(
      String wfsUrl, String typeName, String username, String password) {}

  private WFSTypeNameDescriptor findWFSFeatureType(
      GeoService service, GeoServiceLayer layer, TMFeatureType tmft) {

    String wfsUrl = null;
    String typeName = null;
    String username = null;
    String password = null;
    ServiceAuthentication auth = null;

    if (tmft != null) {
      TMFeatureSource featureSource = tmft.getFeatureSource();

      if (featureSource.getProtocol() == TMFeatureSource.Protocol.WFS) {
        wfsUrl = featureSource.getUrl();
        typeName = tmft.getName();
        auth = featureSource.getAuthentication();
      }
    }

    if ((wfsUrl == null || typeName == null) && service.getProtocol() == GeoServiceProtocol.WMS) {
      // Try to find out the WFS by doing a DescribeLayer request (from OGC SLD spec)
      auth = service.getAuthentication();

      SimpleWFSLayerDescription wfsLayerDescription =
          getWFSLayerDescriptionForWMS(service, layer.getName());
      if (wfsLayerDescription != null
          && wfsLayerDescription.wfsUrl() != null
          && wfsLayerDescription.getFirstTypeName() != null) {
        wfsUrl = wfsLayerDescription.wfsUrl();
        // Ignores possibly multiple feature types associated with the layer (a group layer for
        // instance)
        typeName = wfsLayerDescription.getFirstTypeName();
        auth = service.getAuthentication();
      }
    }

    if (auth != null && auth.getMethod() == ServiceAuthentication.MethodEnum.PASSWORD) {
      username = auth.getUsername();
      password = auth.getPassword();
    }

    if (wfsUrl != null && typeName != null) {
      return new WFSTypeNameDescriptor(wfsUrl, typeName, username, password);
    } else {
      return null;
    }
  }

  private SimpleWFSLayerDescription getWFSLayerDescriptionForWMS(
      GeoService wmsService, String layerName) {
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
    if (wfsLayerDescription != null && !wfsLayerDescription.typeNames().isEmpty()) {
      logger.info(
          "WMS described layer \"{}\" with typeNames \"{}\" of WFS \"{}\" for WMS \"{}\"",
          layerName,
          wfsLayerDescription.typeNames(),
          wfsLayerDescription.wfsUrl(),
          wmsService.getUrl());

      return wfsLayerDescription;
    }
    return null;
  }

  /**
   * Get the (exposed) attribute names of the WFS feature type.
   *
   * @param wfsTypeNameDescriptor provides the WFS feature type to get the attribute names for
   * @return a list of attribute names for the WFS feature type
   * @throws IOException if there were any problems setting up (creating or connecting) the
   *     datasource.
   */
  private static List<String> getWFSAttributeNames(WFSTypeNameDescriptor wfsTypeNameDescriptor)
      throws IOException {
    Map<String, Object> connectionParameters = new HashMap<>();
    connectionParameters.put(
        WFSDataStoreFactory.URL.key,
        SimpleWFSHelper.getWFSRequestURL(wfsTypeNameDescriptor.wfsUrl(), "GetCapabilities")
            .toURL());
    connectionParameters.put(WFSDataStoreFactory.PROTOCOL.key, Boolean.FALSE);
    connectionParameters.put(WFSDataStoreFactory.WFS_STRATEGY.key, "geoserver");
    connectionParameters.put(WFSDataStoreFactory.LENIENT.key, Boolean.TRUE);
    connectionParameters.put(WFSDataStoreFactory.TIMEOUT.key, SimpleWFSHelper.TIMEOUT);
    if (wfsTypeNameDescriptor.username() != null) {
      connectionParameters.put(WFSDataStoreFactory.USERNAME.key, wfsTypeNameDescriptor.username());
      connectionParameters.put(WFSDataStoreFactory.PASSWORD.key, wfsTypeNameDescriptor.password());
    }

    WFSDataStore wfs = new WFSDataStoreFactory().createDataStore(connectionParameters);
    List<String> attributeNames =
        wfs
            .getFeatureSource(wfsTypeNameDescriptor.typeName())
            .getSchema()
            .getAttributeDescriptors()
            .stream()
            .map(AttributeDescriptor::getLocalName)
            .toList();

    wfs.dispose();
    return attributeNames;
  }
}
