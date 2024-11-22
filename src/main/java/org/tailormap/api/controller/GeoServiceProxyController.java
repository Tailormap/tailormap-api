/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;
import static org.tailormap.api.util.HttpProxyUtil.addForwardedForRequestHeaders;
import static org.tailormap.api.util.HttpProxyUtil.passthroughRequestHeaders;
import static org.tailormap.api.util.HttpProxyUtil.passthroughResponseHeaders;
import static org.tailormap.api.util.HttpProxyUtil.setHttpBasicAuthenticationHeader;

import io.micrometer.core.annotation.Timed;
import jakarta.servlet.http.HttpServletRequest;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.tailormap.api.security.AuthorizationService;

/**
 * Proxy controller for OGC WMS and WMTS services. Does not attempt to hide the original service
 * URL. Mostly useful for access to HTTP Basic secured services without sending the credentials to
 * the client. The access control is handled by Spring Security and the authorizations configured on
 * the service.
 *
 * <p>Only supports GET requests. Does not support CORS, only meant for tailormap-viewer from the
 * same origin.
 *
 * <p>Implementation note: uses the Java 11 HttpClient. Spring cloud gateway can proxy with many
 * more features but can not be used in a non-reactive application.
 */
@AppRestController
@Validated
// Can't use ${tailormap-api.base-path} because linkTo() won't work
@RequestMapping(path = "/api/{viewerKind}/{viewerName}/layer/{appLayerId}/proxy/{protocol}")
public class GeoServiceProxyController {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private final AuthorizationService authorizationService;

  public GeoServiceProxyController(AuthorizationService authorizationService) {
    this.authorizationService = authorizationService;
  }

  @RequestMapping(method = {GET, POST})
  @Timed(value = "proxy", description = "Proxy OGC service calls")
  public ResponseEntity<?> proxy(
      @ModelAttribute Application application,
      @ModelAttribute GeoService service,
      @ModelAttribute GeoServiceLayer layer,
      @PathVariable("protocol") GeoServiceProtocol protocol,
      HttpServletRequest request) {

    if (service == null || layer == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    if (GeoServiceProtocol.XYZ.equals(protocol)) {
      throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "XYZ proxying not implemented");
    }

    if (!(service.getProtocol().equals(protocol)
        || GeoServiceProtocol.PROXIEDLEGEND.equals(protocol))) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Invalid proxy protocol: " + protocol);
    }

    if (!service.getSettings().getUseProxy()) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Proxy not enabled for requested service");
    }

    if (authorizationService.mustDenyAccessForSecuredProxy(application, service)) {
      logger.warn(
          "App {} (\"{}\") is using layer \"{}\" from proxied secured service URL {} (username \"{}\"), but app is publicly accessible. Denying proxy, even if user is authenticated.",
          application.getId(),
          application.getName(),
          layer.getName(),
          service.getUrl(),
          service.getAuthentication().getUsername());
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    switch (protocol) {
      case WMS:
      case WMTS:
        return doProxy(buildWMSUrl(service, request), service, request);
      case PROXIEDLEGEND:
        URI legendURI = buildLegendURI(service, layer, request);
        if (null == legendURI) {
          logger.warn("No legend URL found for layer {}", layer.getName());
          return null;
        }
        return doProxy(legendURI, service, request);
      default:
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Unsupported proxy protocol: " + protocol);
    }
  }

  private @Nullable URI buildLegendURI(
      GeoService service, GeoServiceLayer layer, HttpServletRequest request) {
    URI legendURI = GeoServiceHelper.getLayerLegendUrlFromStyles(service, layer);
    if (null != legendURI && null != legendURI.getQuery() && null != request.getQueryString()) {
      // assume this is a getLegendGraphic request
      UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromUri(legendURI);
      switch (service.getSettings().getServerType()) {
        case GEOSERVER:
          uriComponentsBuilder.queryParam(
              "LEGEND_OPTIONS", "fontAntiAliasing:true;labelMargin:0;forceLabels:on");
          break;
        case MAPSERVER:
        case AUTO:
        default:
          // no special options
      }
      if (null != request.getParameterMap().get("SCALE")) {
        legendURI =
            uriComponentsBuilder
                .queryParam("SCALE", request.getParameterMap().get("SCALE")[0])
                .build(true)
                .toUri();
      }
    }
    return legendURI;
  }

  private URI buildWMSUrl(GeoService service, HttpServletRequest request) {
    final UriComponentsBuilder originalServiceUrl =
        UriComponentsBuilder.fromUriString(service.getUrl());
    // request.getParameterMap() includes parameters from an application/x-www-form-urlencoded POST
    // body
    final MultiValueMap<String, String> requestParams =
        request.getParameterMap().entrySet().stream()
            .map(
                entry ->
                    new AbstractMap.SimpleEntry<>(
                        entry.getKey(),
                        Arrays.stream(entry.getValue())
                            .map(value -> UriUtils.encode(value, StandardCharsets.UTF_8))
                            .collect(Collectors.toList())))
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey, Map.Entry::getValue, (x, y) -> y, LinkedMultiValueMap::new));
    final MultiValueMap<String, String> params =
        buildOgcProxyRequestParams(originalServiceUrl.build(true).getQueryParams(), requestParams);
    originalServiceUrl.replaceQueryParams(params);
    return originalServiceUrl.build(true).toUri();
  }

  public static MultiValueMap<String, String> buildOgcProxyRequestParams(
      MultiValueMap<String, String> originalServiceParams,
      MultiValueMap<String, String> requestParams) {
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

  private static ResponseEntity<?> doProxy(
      URI uri, GeoService service, HttpServletRequest request) {
    final HttpClient.Builder builder =
        HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL);
    final HttpClient httpClient = builder.build();

    HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().uri(uri);

    addForwardedForRequestHeaders(requestBuilder, request);

    passthroughRequestHeaders(
        requestBuilder,
        request,
        Set.of(
            "Accept",
            "If-Modified-Since",
            "If-Unmodified-Since",
            "If-Match",
            "If-None-Match",
            "If-Range",
            "Range",
            "Referer",
            "User-Agent"));

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
          httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());

      // If the server does not accept our credentials it might 'hide' the layer or even send a 401
      // Unauthorized status. We do not send the WWW-Authenticate header back, so the client will
      // get the error but not an authorization popup.
      // It would be nice if proxy (auth) errors were logged and available in the admin interface.
      // Currently, a layer will just stop working if the geo service credentials are changed
      // without updating them in the geo service registry.
      InputStreamResource body = new InputStreamResource(response.body());
      HttpHeaders headers =
          passthroughResponseHeaders(
              response.headers(),
              Set.of(
                  "Content-Type",
                  "Content-Length",
                  "Content-Range",
                  "Content-Disposition",
                  "Cache-Control",
                  "Expires",
                  "Last-Modified",
                  "ETag",
                  "Pragma"));
      return ResponseEntity.status(response.statusCode()).headers(headers).body(body);
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("Bad Gateway");
    }
  }
}
