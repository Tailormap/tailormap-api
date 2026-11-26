/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller;

import static org.springframework.http.HttpHeaders.ACCEPT;
import static org.springframework.http.HttpHeaders.CACHE_CONTROL;
import static org.springframework.http.HttpHeaders.CONTENT_DISPOSITION;
import static org.springframework.http.HttpHeaders.CONTENT_LENGTH;
import static org.springframework.http.HttpHeaders.CONTENT_RANGE;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.HttpHeaders.ETAG;
import static org.springframework.http.HttpHeaders.EXPIRES;
import static org.springframework.http.HttpHeaders.IF_MATCH;
import static org.springframework.http.HttpHeaders.IF_MODIFIED_SINCE;
import static org.springframework.http.HttpHeaders.IF_NONE_MATCH;
import static org.springframework.http.HttpHeaders.IF_RANGE;
import static org.springframework.http.HttpHeaders.IF_UNMODIFIED_SINCE;
import static org.springframework.http.HttpHeaders.LAST_MODIFIED;
import static org.springframework.http.HttpHeaders.PRAGMA;
import static org.springframework.http.HttpHeaders.RANGE;
import static org.springframework.http.HttpHeaders.REFERER;
import static org.springframework.http.HttpHeaders.USER_AGENT;
import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;
import static org.tailormap.api.persistence.helper.GeoServiceHelper.getWmsRequest;
import static org.tailormap.api.util.HttpProxyUtil.addForwardedForRequestHeaders;
import static org.tailormap.api.util.HttpProxyUtil.configureProxyRequestBuilderForUri;
import static org.tailormap.api.util.HttpProxyUtil.passthroughRequestHeaders;
import static org.tailormap.api.util.HttpProxyUtil.passthroughResponseHeaders;
import static org.tailormap.api.util.HttpProxyUtil.setHttpBasicAuthenticationHeader;

import io.micrometer.core.annotation.Timed;
import jakarta.servlet.http.HttpServletRequest;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;
import org.tailormap.api.annotation.AppRestController;
import org.tailormap.api.persistence.Application;
import org.tailormap.api.persistence.GeoService;
import org.tailormap.api.persistence.helper.GeoServiceHelper;
import org.tailormap.api.persistence.json.GeoServiceLayer;
import org.tailormap.api.persistence.json.GeoServiceProtocol;
import org.tailormap.api.persistence.json.ServiceAuthentication;
import org.tailormap.api.security.AuthorisationService;

/**
 * Proxy controller for OGC WMS, WMTS, and 3D Tiles services. Does not attempt to hide the original service URL. Mostly
 * useful for access to HTTP Basic secured services without sending the credentials to the client. The access control is
 * handled by Spring Security and the authorizations configured on the service.
 *
 * <p>Only supports GET requests. Does not support CORS, only meant for tailormap-viewer from the same origin.
 *
 * <p>Implementation note: uses the Java 11 HttpClient. Spring cloud gateway can proxy with many more features but
 * cannot be used in a non-reactive application.
 */
@AppRestController
@Validated
// Can't use ${tailormap-api.base-path} because linkTo() won't work
@RequestMapping(path = "/api/{viewerKind}/{viewerName}/layer/{appLayerId}/proxy")
public class GeoServiceProxyController {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private final AuthorisationService authorisationService;
  private final HttpClient httpClient;

  public static final String TILES3D_DESCRIPTION_PATH = "tiles3dDescription";

  @Value("${tailormap-api.proxy.passthrough.layerpatterns:}")
  private Set<String> proxyLayerPassthroughPatterns = Set.of();

  @Value("${tailormap-api.proxy.passthrough.hostnames:}")
  private Set<String> proxyPassthroughHostNames = Set.of();

  /** Parameters (case-insensitive) which name the layer(s) to request from a WMS or WMTS. */
  private static final List<String> LAYER_PARAMS = List.of("LAYERS", "QUERY_LAYERS", "LAYER");

  /** Parameters (case-insensitive) with an SLD, which can name any layer of a WMS. */
  private static final List<String> SLD_PARAMS = List.of("SLD", "SLD_BODY", "SLD_URL");

  /** Allowed WMS operations (lowercase) and the layer parameters each requires. */
  private static final Map<String, Set<String>> WMS_REQUEST_LAYER_PARAMS = Map.of(
      "getcapabilities", Set.of(),
      "getmap", Set.of("LAYERS"),
      "getfeatureinfo", Set.of("LAYERS", "QUERY_LAYERS"),
      "getlegendgraphic", Set.of("LAYER"));

  /** Allowed WMTS (KVP) operations (lowercase) and the layer parameters each requires. */
  private static final Map<String, Set<String>> WMTS_REQUEST_LAYER_PARAMS = Map.of(
      "getcapabilities", Set.of(),
      "gettile", Set.of("LAYER"),
      "getfeatureinfo", Set.of("LAYER"));

  /** Parameters a request to the legend proxy may not set, so the legend is always for the app layer. */
  private static final List<String> LEGEND_FORBIDDEN_PARAMS =
      List.of("LAYER", "LAYERS", "QUERY_LAYERS", "SLD", "SLD_BODY", "SLD_URL");

  /** Parameters from a request to the legend proxy which are not copied to the legend URL. */
  private static final Set<String> LEGEND_IGNORED_PARAMS = Set.of("SERVICE", "REQUEST");

  public GeoServiceProxyController(AuthorisationService authorisationService) {
    this.authorisationService = authorisationService;

    final HttpClient.Builder builder = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL);
    this.httpClient = builder.build();
  }

  @RequestMapping(
      method = {GET, POST},
      path = "/tiles3d/**")
  public ResponseEntity<?> proxy3dtiles(
      @ModelAttribute Application application,
      @ModelAttribute GeoService service,
      @ModelAttribute GeoServiceLayer layer,
      HttpServletRequest request) {

    checkRequestValidity(application, service, layer, GeoServiceProtocol.TILES3D, request);

    return doProxy(build3DTilesUrl(service, request), service, request);
  }

  @RequestMapping(
      method = {GET, POST},
      path = "/{protocol}")
  @Timed(value = "proxy", description = "Proxy OGC service calls")
  public ResponseEntity<?> proxy(
      @ModelAttribute Application application,
      @ModelAttribute GeoService service,
      @ModelAttribute GeoServiceLayer layer,
      @PathVariable("protocol") GeoServiceProtocol protocol,
      HttpServletRequest request) {

    checkRequestValidity(application, service, layer, protocol, request);

    return switch (protocol) {
      case WMS, WMTS -> doProxy(buildWMSUrl(service, request), service, request);
      case LEGEND -> {
        URI legendURI = buildLegendURI(service, layer, request);
        if (legendURI == null) {
          logger.warn("No legend URL found for layer {}", layer.getName());
          yield null;
        }
        yield doProxy(legendURI, service, request);
      }
      case TILES3D ->
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Incorrect 3D Tiles proxy request: No path to capabilities or content");
      default ->
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported proxy protocol: " + protocol);
    };
  }

  private void checkRequestValidity(
      Application application,
      GeoService service,
      GeoServiceLayer layer,
      GeoServiceProtocol protocol,
      HttpServletRequest request) {
    if (service == null || layer == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    if (GeoServiceProtocol.XYZ.equals(protocol)) {
      throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "XYZ proxying not implemented");
    }

    if (!(service.getProtocol().equals(protocol) || GeoServiceProtocol.LEGEND.equals(protocol))) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid proxy protocol: " + protocol);
    }

    if (!service.getSettings().getUseProxy()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Proxy not enabled for requested service");
    }

    if (GeoServiceProtocol.WMS.equals(protocol) || GeoServiceProtocol.WMTS.equals(protocol)) {
      checkOgcRequestParams(service, layer, protocol, request.getParameterMap());
    } else if (GeoServiceProtocol.LEGEND.equals(protocol)) {
      checkLegendRequestParams(request.getParameterMap());
    }

    if (authorisationService.mustDenyAccessForSecuredProxy(service)) {
      logger.debug(
          "Denying proxy for layer \"{}\" in app #{} (\"{}\") from secured service #{} (URL {}): user is not authenticated",
          layer.getName(),
          application.getId(),
          application.getName(),
          service.getId(),
          service.getUrl());
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
  }

  /**
   * Checks the parameters of a WMS or WMTS request so that only the app layer (or a layer allowed by the passthrough
   * configuration) can be requested from the proxied service. Only the operations needed by the viewer are allowed,
   * and the parameters which name the layer(s) to request are required for operations which return layer content.
   */
  void checkOgcRequestParams(
      GeoService service, GeoServiceLayer layer, GeoServiceProtocol protocol, Map<String, String[]> params) {
    String serviceParam = getSingleParam(params, "SERVICE");
    if (serviceParam != null && !protocol.getValue().equalsIgnoreCase(serviceParam)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid SERVICE parameter for proxy protocol");
    }

    String requestParam = getSingleParam(params, "REQUEST");
    Map<String, Set<String>> allowedRequests =
        GeoServiceProtocol.WMS.equals(protocol) ? WMS_REQUEST_LAYER_PARAMS : WMTS_REQUEST_LAYER_PARAMS;
    Set<String> requiredLayerParams =
        requestParam == null ? null : allowedRequests.get(requestParam.toLowerCase(Locale.ROOT));
    if (requiredLayerParams == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing or unsupported REQUEST parameter for proxy protocol");
    }

    for (String requiredParam : requiredLayerParams) {
      if (getSingleParam(params, requiredParam) == null) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing " + requiredParam + " parameter");
      }
    }

    // Check all parameters naming layers, also when they are not required for the requested operation
    for (String layerParam : LAYER_PARAMS) {
      String value = getSingleParam(params, layerParam);
      if (value == null) {
        continue;
      }
      if (value.indexOf(',') >= 0) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Multiple layers in " + layerParam + " parameter not supported");
      }
      checkLayerName(service, layer, value);
    }

    // An SLD can reference any layer of the service, so it would bypass the layer name checks above
    for (String sldParam : SLD_PARAMS) {
      if (hasParam(params, sldParam)) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, sldParam + " parameter not allowed for proxied services");
      }
    }
  }

  /**
   * The legend proxy uses the legend URL from the capabilities for the app layer. The request may add parameters to
   * enhance the legend image, but may not change which layer the legend is for.
   */
  static void checkLegendRequestParams(Map<String, String[]> params) {
    for (String param : LEGEND_FORBIDDEN_PARAMS) {
      if (hasParam(params, param)) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, param + " parameter not allowed for proxied legend");
      }
    }
  }

  private void checkLayerName(GeoService service, GeoServiceLayer layer, String layerNameParamValue) {
    if (layer.getName().equals(layerNameParamValue)) {
      return;
    }

    // check if layer matches any passthrough pattern, if not throw bad request
    if (proxyLayerPassthroughPatterns.stream().noneMatch(pattern -> {
      return Pattern.compile(pattern.formatted(Pattern.quote(layer.getName())))
          .matcher(layerNameParamValue)
          .matches();
    })) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Requested layer name does not match expected layer");
    }

    if (!proxyPassthroughHostNames.isEmpty()) {
      // check if host matches any passthrough hostname, if not throw bad request
      try {
        String geoServiceHostName = new URI(service.getUrl()).getHost();
        if (proxyPassthroughHostNames.stream()
            .noneMatch(hostname -> hostname.equalsIgnoreCase(geoServiceHostName))) {
          throw new ResponseStatusException(
              HttpStatus.BAD_REQUEST,
              "Requested service hostname does not match allowed hostnames for layer passthrough");
        }
      } catch (URISyntaxException e) {
        logger.error(
            "Invalid service URL \"{}\" for layer id {}: {}",
            service.getUrl(),
            layer.getId(),
            e.getMessage());
        throw new ResponseStatusException(
            HttpStatus.INTERNAL_SERVER_ERROR, "Invalid service URL in configuration");
      }
    }
  }

  private static boolean hasParam(Map<String, String[]> params, String name) {
    return params.keySet().stream().anyMatch(name::equalsIgnoreCase);
  }

  /**
   * Returns the value of a parameter (case-insensitive name), or null if not present. Multiple parameters with the
   * same name (in any case) or multiple values are rejected, because the proxied service may use another one than the
   * value checked here.
   */
  private static @Nullable String getSingleParam(Map<String, String[]> params, String name) {
    List<String> values = params.entrySet().stream()
        .filter(entry -> name.equalsIgnoreCase(entry.getKey()))
        .flatMap(entry -> Arrays.stream(entry.getValue()))
        .toList();
    if (values.size() > 1) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          LAYER_PARAMS.contains(name)
              ? "Multiple layers in " + name + " parameter not supported"
              : "Multiple " + name + " parameters not supported");
    }
    return values.isEmpty() ? null : values.getFirst();
  }

  private @Nullable URI buildLegendURI(GeoService service, GeoServiceLayer layer, HttpServletRequest request) {
    URI legendURI = GeoServiceHelper.getLayerLegendUrlFromStyles(service, layer);
    if (legendURI == null) {
      return null;
    }

    // If the original service legend URL is not a GetLegendGraphic request, do not add any parameters from the
    // incoming request and proxy the request as is
    String wmsRequest = getWmsRequest(legendURI);
    if (!"getlegendgraphic".equalsIgnoreCase(wmsRequest)) {
      return legendURI;
    }

    return addLegendRequestParams(legendURI, request.getParameterMap());
  }

  /**
   * Adds the parameters from the incoming request to a GetLegendGraphic URL to allow for vendor-specific parameters
   * to enhance the legend image, such as font antialiasing, hi-DPI, label margins, and so on. Parameters which would
   * change the layer of the legend are rejected by {@link #checkLegendRequestParams(Map)}. The SERVICE and REQUEST
   * parameters of the legend URL are kept.
   */
  static URI addLegendRequestParams(URI legendURI, @Nullable Map<String, String[]> requestParams) {
    UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromUri(legendURI);
    MultiValueMap<String, String> legendParams =
        new LinkedMultiValueMap<>(uriComponentsBuilder.build(true).getQueryParams());
    if (requestParams != null) {
      requestParams.forEach((key, values) -> {
        if (values.length == 0 || LEGEND_IGNORED_PARAMS.contains(key.toUpperCase(Locale.ROOT))) {
          return;
        }
        // Replace the parameter case-insensitively, so it is not sent twice with different values
        legendParams.keySet().removeIf(key::equalsIgnoreCase);
        legendParams.add(key, UriUtils.encode(values[values.length - 1], StandardCharsets.UTF_8));
      });
    }
    uriComponentsBuilder.replaceQueryParams(legendParams);
    return uriComponentsBuilder.build(true).toUri();
  }

  private URI buildWMSUrl(GeoService service, HttpServletRequest request) {
    final UriComponentsBuilder originalServiceUrl = UriComponentsBuilder.fromUriString(service.getUrl());
    // request.getParameterMap() includes parameters from an application/x-www-form-urlencoded POST
    // body
    final MultiValueMap<String, String> requestParams = request.getParameterMap().entrySet().stream()
        .map(entry -> new AbstractMap.SimpleEntry<>(
            entry.getKey(),
            Arrays.stream(entry.getValue())
                .map(value -> UriUtils.encode(value, StandardCharsets.UTF_8))
                .collect(Collectors.toList())))
        .collect(Collectors.toMap(
            Map.Entry::getKey, Map.Entry::getValue, (x, y) -> y, LinkedMultiValueMap::new));
    final MultiValueMap<String, String> params =
        buildOgcProxyRequestParams(originalServiceUrl.build(true).getQueryParams(), requestParams);
    originalServiceUrl.replaceQueryParams(params);
    return originalServiceUrl.build(true).toUri();
  }

  public static MultiValueMap<String, String> buildOgcProxyRequestParams(
      MultiValueMap<String, String> originalServiceParams, MultiValueMap<String, String> requestParams) {
    // Start with all the parameters from the request
    final MultiValueMap<String, String> params = new LinkedMultiValueMap<>(requestParams);

    // Add original service URL parameters if they are not required OGC params (case-insensitive). These take
    // precedence over the request parameters (case-insensitive), so a request can't change parameters such as
    // MapServer's MAP parameter to access other layers with the credentials of the service.
    final List<String> ogcParams = List.of("SERVICE", "REQUEST", "VERSION");
    for (Map.Entry<String, List<String>> serviceParam : originalServiceParams.entrySet()) {
      String key = serviceParam.getKey();
      if (!ogcParams.contains(key.toUpperCase(Locale.ROOT))) {
        params.keySet().removeIf(key::equalsIgnoreCase);
        params.put(key, serviceParam.getValue());
      }
    }
    return params;
  }

  private URI build3DTilesUrl(GeoService service, HttpServletRequest request) {
    // The URL in the GeoService refers to the location of the JSON file describing the tileset,
    // e.g. example.com/buildings/3dtiles. The paths to the subtrees and tiles of the tilesets do
    // not include the '/3dtiles' (or '/tileset.json') part of the path. Their paths are e.g.
    // example.com/buildings/subtrees/... or example.com/buildings/t/...
    final UriComponentsBuilder originalServiceUrl = UriComponentsBuilder.fromUriString(service.getUrl());
    String baseUrl = originalServiceUrl.build(true).toUriString();
    String[] parts = request.getRequestURI().split("/proxy/tiles3d/", 2);
    if (parts.length < 2) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid 3D tiles proxy path");
    }
    String pathToContent = parts[1];

    // Prevent path traversal
    if (pathToContent.contains("..") || pathToContent.contains("\\")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid 3D tiles content path");
    }

    // Return service URL when the request is for the JSON file describing the tileset
    if (Objects.equals(pathToContent, TILES3D_DESCRIPTION_PATH)) {
      return UriComponentsBuilder.fromUriString(baseUrl).build(true).toUri();
    }

    // Remove the part of the service URL referring to the JSON file describing the tileset
    int lastSlashIndex = baseUrl.lastIndexOf('/');
    if (lastSlashIndex != -1) {
      baseUrl = baseUrl.substring(0, lastSlashIndex + 1);
    }

    // Return final URL with specific path to the tile or subtree
    String finalUrl = baseUrl + pathToContent;
    return UriComponentsBuilder.fromUriString(finalUrl).build(true).toUri();
  }

  private ResponseEntity<?> doProxy(URI uri, GeoService service, HttpServletRequest request) {
    HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();

    configureProxyRequestBuilderForUri(requestBuilder, uri, request);

    addForwardedForRequestHeaders(requestBuilder, request);

    passthroughRequestHeaders(
        requestBuilder,
        request,
        Set.of(
            ACCEPT,
            IF_MODIFIED_SINCE,
            IF_UNMODIFIED_SINCE,
            IF_MATCH,
            IF_NONE_MATCH,
            IF_RANGE,
            RANGE,
            REFERER,
            USER_AGENT));

    if (service.getAuthentication() != null
        && service.getAuthentication().getMethod() == ServiceAuthentication.MethodEnum.PASSWORD) {
      setHttpBasicAuthenticationHeader(
          requestBuilder,
          service.getAuthentication().getUsername(),
          service.getAuthentication().getPassword());
    }

    try {
      // TODO: close JPA connection before proxying
      HttpResponse<InputStream> response =
          this.httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());

      // If the server does not accept our credentials, it might 'hide' the layer or even send a 401
      // Unauthorized status. We do not send the WWW-Authenticate header back, so the client will
      // get the error but not an authorization popup.
      // It would be nice if proxy (auth) errors were logged and available in the admin interface.
      // Currently, a layer will just stop working if the geo service credentials are changed
      // without updating them in the geo service registry.
      InputStreamResource body = new InputStreamResource(response.body());
      HttpHeaders headers = passthroughResponseHeaders(
          response.headers(),
          Set.of(
              CONTENT_TYPE,
              CONTENT_LENGTH,
              CONTENT_RANGE,
              CONTENT_DISPOSITION,
              CACHE_CONTROL,
              EXPIRES,
              LAST_MODIFIED,
              ETAG,
              PRAGMA));
      return ResponseEntity.status(response.statusCode()).headers(headers).body(body);
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("Bad Gateway");
    }
  }
}
