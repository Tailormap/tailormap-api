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
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
import org.springframework.web.context.request.ServletWebRequest;
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
  private final Duration requestTimeout;

  public static final String TILES3D_DESCRIPTION_PATH = "tiles3dDescription";

  @Value("${tailormap-api.proxy.passthrough.layerpatterns:}")
  private Set<String> proxyLayerPassthroughPatterns = Set.of();

  @Value("${tailormap-api.proxy.passthrough.hostnames:}")
  private Set<String> proxyPassthroughHostNames = Set.of();

  public GeoServiceProxyController(
      AuthorisationService authorisationService,
      @Value("${tailormap-api.proxy.connect-timeout:10s}") Duration connectTimeout,
      @Value("${tailormap-api.proxy.request-timeout:60s}") Duration requestTimeout) {
    this.authorisationService = authorisationService;
    this.requestTimeout = requestTimeout;

    final HttpClient.Builder builder = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(connectTimeout);
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

    final URI uri;
    try {
      uri = build3DTilesUrl(service, request);
    } catch (IllegalArgumentException e) {
      throw invalidServiceUrl(service, layer, e);
    }
    return doProxy(uri, service, request);
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
      case WMS, WMTS -> {
        final URI uri;
        try {
          uri = buildWMSUrl(service, request);
        } catch (IllegalArgumentException e) {
          throw invalidServiceUrl(service, layer, e);
        }
        yield doProxy(uri, service, request);
      }
      case LEGEND -> {
        final URI legendURI;
        try {
          legendURI = buildLegendURI(service, layer, request);
        } catch (IllegalArgumentException e) {
          throw invalidServiceUrl(service, layer, e);
        }
        if (legendURI == null) {
          logger.debug("No legend URL found for layer {} of service {}", layer.getName(), service.getId());
          throw new ResponseStatusException(
              HttpStatus.NOT_FOUND, "No legend URL for layer \"%s\"".formatted(layer.getName()));
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

    // check if there are multiple LAYERS parameters or multiple values for the LAYERS parameter,
    // which is not supported by this proxy and can be a sign of an attempt to bypass the layer name check below
    long wmsLayerParamCount = request.getParameterMap().entrySet().stream()
        .filter(entry -> "LAYERS".equalsIgnoreCase(entry.getKey()))
        .count();
    boolean hasMultipleLayerValues = request.getParameterMap().entrySet().stream()
        .filter(entry -> "LAYERS".equalsIgnoreCase(entry.getKey()))
        .anyMatch(entry -> entry.getValue().length > 1
            || (entry.getValue().length == 1 && entry.getValue()[0].indexOf(',') >= 0));
    if (wmsLayerParamCount > 1 || hasMultipleLayerValues) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Multiple layers in LAYERS parameter not supported");
    }

    // this can be null in case of requests that do not have a LAYERS parameter, such as GetCapabilities requests.
    String layerNameParamValue = request.getParameterMap().entrySet().stream()
        .filter(entry -> "LAYERS".equalsIgnoreCase(entry.getKey()))
        .findFirst()
        .map(entry -> entry.getValue()[0])
        .orElse(null);

    if (layerNameParamValue != null && !layer.getName().equals(layerNameParamValue)) {
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
   * Log and create an exception for a (legend) URL from the service configuration or capabilities that cannot be
   * parsed or built, for instance because it contains unencoded characters. Without this a bare server error without
   * a message would be returned.
   */
  private static ResponseStatusException invalidServiceUrl(
      GeoService service, GeoServiceLayer layer, IllegalArgumentException e) {
    logger.error(
        "Invalid URL for proxying layer \"{}\" of service {}: {}",
        layer.getName(),
        service.getId(),
        e.getMessage());
    return new ResponseStatusException(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Invalid URL for proxying layer \"%s\" of service %s: %s"
            .formatted(layer.getName(), service.getId(), e.getMessage()),
        e);
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

    // Add all parameters from the incoming request to allow for vendor-specific parameters to enhance the legend
    // image, such as font antialiasing, hi-DPI, label margins, and so on
    UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromUri(legendURI);
    if (request.getParameterMap() != null) {
      request.getParameterMap().forEach((key, values) -> {
        for (String value : values) {
          uriComponentsBuilder.replaceQueryParam(
              UriUtils.encode(key, StandardCharsets.UTF_8),
              UriUtils.encode(value, StandardCharsets.UTF_8));
        }
      });
    }
    // Make sure the REQUEST parameter is set to GetLegendGraphic. No SSRF risk as WMSes should not do anything with
    // other parameters other than returning a legend image
    uriComponentsBuilder.replaceQueryParam("REQUEST", "GetLegendGraphic");

    return uriComponentsBuilder.build(true).toUri();
  }

  private URI buildWMSUrl(GeoService service, HttpServletRequest request) {
    final UriComponentsBuilder originalServiceUrl = UriComponentsBuilder.fromUriString(service.getUrl());
    // request.getParameterMap() includes parameters from an application/x-www-form-urlencoded POST
    // body
    final MultiValueMap<String, String> requestParams = request.getParameterMap().entrySet().stream()
        .map(entry -> new AbstractMap.SimpleEntry<>(
            UriUtils.encode(entry.getKey(), StandardCharsets.UTF_8),
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

    // Add original service URL parameters if they are not required OGC params (case-insensitive)
    // and not already set by request
    final List<String> ogcParams = List.of(new String[] {"SERVICE", "REQUEST", "VERSION"});
    for (Map.Entry<String, List<String>> serviceParam : originalServiceParams.entrySet()) {
      if (!params.containsKey(serviceParam.getKey())
          && !ogcParams.contains(serviceParam.getKey().toUpperCase(Locale.ROOT))) {
        params.put(serviceParam.getKey(), serviceParam.getValue());
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
    HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().timeout(requestTimeout);

    try {
      configureProxyRequestBuilderForUri(requestBuilder, uri, request);
    } catch (IllegalArgumentException e) {
      // For instance an unsupported URI scheme in the service URL
      logger.error("Invalid URL for proxying service {}: {}", service.getId(), e.getMessage());
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "Invalid URL for proxying service %s: %s".formatted(service.getId(), e.getMessage()),
          e);
    }

    addForwardedForRequestHeaders(requestBuilder, request);

    try {
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
    } catch (IllegalArgumentException e) {
      // HttpRequest.Builder.header() rejects header values with illegal characters
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Invalid request header for proxying: " + e.getMessage(), e);
    }

    if (service.getAuthentication() != null
        && service.getAuthentication().getMethod() == ServiceAuthentication.MethodEnum.PASSWORD) {
      setHttpBasicAuthenticationHeader(
          requestBuilder,
          service.getAuthentication().getUsername(),
          service.getAuthentication().getPassword());
    }

    final HttpResponse<InputStream> response;
    try {
      // TODO: close JPA connection before proxying
      response = this.httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());
    } catch (HttpTimeoutException e) {
      // Also handles HttpConnectTimeoutException
      throw upstreamError(HttpStatus.GATEWAY_TIMEOUT, "Timeout proxying request to", uri, service, e);
    } catch (IOException | RuntimeException e) {
      throw upstreamError(HttpStatus.BAD_GATEWAY, "Error proxying request to", uri, service, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw upstreamError(
          HttpStatus.SERVICE_UNAVAILABLE, "Interrupted while proxying request to", uri, service, e);
    }

    // If the server does not accept our credentials, it might 'hide' the layer or even send a 401
    // Unauthorized status. We do not send the WWW-Authenticate header back, so the client will
    // get the error but not an authorization popup.
    // It would be nice if proxy (auth) errors were logged and available in the admin interface.
    // Currently, a layer will just stop working if the geo service credentials are changed
    // without updating them in the geo service registry.
    if (response.statusCode() == HttpStatus.OK.value()
        && GET.name().equals(request.getMethod())
        && isNotModified(request, response.headers())) {
      // Spring would turn the 200 response into a 304 Not Modified response itself when the passed through ETag
      // or
      // Last-Modified header matches the conditional request headers (when the proxied service ignores them), but
      // without closing the response body. That would keep the connection to the proxied service open, so handle
      // this here.
      closeQuietly(response.body());
      return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
          .headers(passthroughResponseHeaders(
              response.headers(), Set.of(CACHE_CONTROL, EXPIRES, LAST_MODIFIED, ETAG)))
          .build();
    }

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
  }

  private static boolean isNotModified(HttpServletRequest request, java.net.http.HttpHeaders upstreamHeaders) {
    HttpHeaders headers = passthroughResponseHeaders(upstreamHeaders, Set.of(ETAG, LAST_MODIFIED));
    // getLastModified() returns -1 when the header is missing or invalid
    long lastModified = headers.getLastModified();
    if (headers.getETag() == null && lastModified < 0) {
      return false;
    }
    return new ServletWebRequest(request).checkNotModified(headers.getETag(), lastModified);
  }

  private static void closeQuietly(InputStream in) {
    try {
      in.close();
    } catch (IOException e) {
      logger.debug("Error closing proxied response body", e);
    }
  }

  /**
   * Log a failed request to the proxied service and create an exception to report it to the client. Only the scheme,
   * host, port and path of the URI are logged and reported, because the user info and query may contain credentials.
   * The request may also have a Basic authentication header, which is never logged.
   */
  private static ResponseStatusException upstreamError(
      HttpStatus status, String what, URI uri, GeoService service, Exception e) {
    String safeUri = withoutUserInfoAndQuery(uri);
    String reason = describeException(e);
    logger.warn("{} service {} at {}: {}", what, service.getId(), safeUri, reason);
    return new ResponseStatusException(status, "%s service at %s: %s".formatted(what, safeUri, reason), e);
  }

  /**
   * Describe an exception from the HTTP client. Some exceptions such as {@link java.net.ConnectException} have no
   * message but have a cause with a message, such as "Connection refused".
   */
  private static String describeException(Exception e) {
    if (e.getMessage() != null && !e.getMessage().isBlank()) {
      return e.getMessage();
    }
    for (Throwable t = e.getCause(); t != null; t = t.getCause()) {
      if (t.getMessage() != null && !t.getMessage().isBlank()) {
        return "%s: %s".formatted(e.getClass().getSimpleName(), t.getMessage());
      }
    }
    return e.getClass().getSimpleName();
  }

  static String withoutUserInfoAndQuery(URI uri) {
    try {
      return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), null, null).toString();
    } catch (URISyntaxException e) {
      return "(invalid URI)";
    }
  }
}
