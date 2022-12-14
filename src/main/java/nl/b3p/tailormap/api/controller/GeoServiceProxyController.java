/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

import nl.b3p.tailormap.api.annotation.AppRestController;
import nl.tailormap.viewer.config.app.Application;
import nl.tailormap.viewer.config.app.ApplicationLayer;
import nl.tailormap.viewer.config.services.GeoService;
import nl.tailormap.viewer.config.services.TileService;
import nl.tailormap.viewer.config.services.WMSService;

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
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.io.InputStream;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

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
@RequestMapping(path = "/app/{appId}/layer/{appLayerId}/proxy/{protocol}")
public class GeoServiceProxyController {

    @RequestMapping(method = {GET, POST})
    public ResponseEntity<?> proxy(
            @ModelAttribute Application application,
            @ModelAttribute ApplicationLayer applicationLayer,
            @PathVariable("protocol") String protocol,
            HttpServletRequest request) {

        Predicate<GeoService> serviceValidator;
        if ("wmts".equals(protocol)) {
            serviceValidator =
                    (GeoService gs) ->
                            TileService.PROTOCOL.equals(gs.getProtocol())
                                    && TileService.TILING_PROTOCOL_WMTS.equals(
                                            ((TileService) gs).getTilingProtocol());
        } else if ("wms".equals(protocol)) {
            serviceValidator = (GeoService gs) -> WMSService.PROTOCOL.equals(gs.getProtocol());
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Unknown proxy protocol: " + protocol);
        }

        final GeoService service = applicationLayer.getService();

        if (!serviceValidator.test(service)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Invalid proxy protocol for service");
        }

        if (!Boolean.parseBoolean(
                String.valueOf(service.getDetails().get(GeoService.DETAIL_USE_PROXY)))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Proxy not enabled for requested service");
        }

        final UriComponentsBuilder originalServiceUrl =
                UriComponentsBuilder.fromHttpUrl(service.getUrl());
        // request.getParameterMap() includes parameters from an
        // application/x-www-form-urlencoded POST body
        final MultiValueMap<String, String> requestParams =
                request.getParameterMap().entrySet().stream()
                        .map(
                                entry ->
                                        new AbstractMap.SimpleEntry<>(
                                                entry.getKey(),
                                                Arrays.stream(entry.getValue())
                                                        .map(
                                                                value ->
                                                                        UriUtils.encode(
                                                                                value,
                                                                                StandardCharsets
                                                                                        .UTF_8))
                                                        .collect(Collectors.toList())))
                        .collect(
                                Collectors.toMap(
                                        Map.Entry::getKey,
                                        Map.Entry::getValue,
                                        (x, y) -> y,
                                        LinkedMultiValueMap::new));
        final MultiValueMap<String, String> params =
                buildOgcProxyRequestParams(
                        originalServiceUrl.build(true).getQueryParams(), requestParams);
        originalServiceUrl.replaceQueryParams(params);

        return doProxy(originalServiceUrl.build(true).toUri(), service, request);
    }

    public static MultiValueMap<String, String> buildOgcProxyRequestParams(
            MultiValueMap<String, String> originalServiceParams,
            MultiValueMap<String, String> requestParams) {
        // Start with all the parameters from the request
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>(requestParams);

        // Add original service URL parameters if they are not required OGC params
        // (case-insensitive) and not already set by request
        final List<String> ogcParams = List.of(new String[] {"SERVICE", "REQUEST", "VERSION"});
        for (Map.Entry<String, List<String>> serviceParam : originalServiceParams.entrySet()) {
            if (!params.containsKey(serviceParam.getKey())
                    && !ogcParams.contains(serviceParam.getKey().toUpperCase())) {
                params.put(serviceParam.getKey(), serviceParam.getValue());
            }
        }
        return params;
    }

    private static ResponseEntity<?> doProxy(
            URI uri, GeoService service, HttpServletRequest request) {
        final HttpClient.Builder builder =
                HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL);

        // Some HTTP services may not like HTTP/2 (not configurable in admin at the moment)
        if (Boolean.parseBoolean(String.valueOf(service.getDetails().get("proxy_http1_1")))) {
            builder.version(HttpClient.Version.HTTP_1_1);
        }

        final HttpClient httpClient = builder.build();

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().uri(uri);

        try {
            String ip = request.getRemoteAddr();
            InetAddress inetAddress = InetAddress.getByName(ip);

            if (inetAddress instanceof Inet6Address) {
                // https://stackoverflow.com/questions/33168783/
                Inet6Address inet6Address = (Inet6Address) inetAddress;
                int scopeId = inet6Address.getScopeId();

                if (scopeId > 0) {
                    ip = inet6Address.getHostName().replaceAll("%" + scopeId + "$", "");
                }

                // IPv6 address must be bracketed and quoted
                ip = "\"[" + ip + "]\"";
            }
            requestBuilder.header("X-Forwarded-For", ip);
            requestBuilder.header("Forwarded", "for=" + ip);
        } catch (UnknownHostException ignored) {
            // Don't care
        }

        String[] requestHeaders = {
            "Accept",
            "If-Modified-Since",
            "If-Unmodified-Since",
            "If-Match",
            "If-None-Match",
            "If-Range",
            "Range",
            "Referer",
            "User-Agent",
        };
        for (String header : requestHeaders) {
            String value = request.getHeader(header);
            if (value != null) {
                requestBuilder.header(header, value);
            }
        }

        if (service.getUsername() != null && service.getPassword() != null) {
            String toEncode = service.getUsername() + ":" + service.getPassword();
            requestBuilder.header(
                    "Authorization",
                    "Basic "
                            + Base64.getEncoder()
                                    .encodeToString(toEncode.getBytes(StandardCharsets.UTF_8)));

            // TODO: in some cases we might want to forward the authenticated tailormap user or
            // groups/roles to the service (perhaps in a secret header name) so the service can
            // apply different authorizations instead of just one account.
        }

        try {
            HttpResponse<InputStream> response =
                    httpClient.send(
                            requestBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());
            // If the server does not accept our credentials it might 'hide' the layer or even send
            // a 401 Unauthorized status. We do not send the WWW-Authenticate header back, so the
            // client will get the error but not an authorization popup.
            // It would be nice if proxy (auth) errors were logged and available in the admin
            // interface. Currently, a layer will just stop working if the geo service credentials
            // are changed without updating them in the geo service registry.
            InputStreamResource body = new InputStreamResource(response.body());
            org.springframework.http.HttpHeaders headers = new HttpHeaders();
            // TODO for WMTS, does caching work?
            String[] allowedResponseHeaders = {
                "Content-Type",
                "Content-Length",
                "Content-Range",
                "Content-Disposition",
                "Cache-Control",
                "Expires",
                "Last-Modified",
                "ETag",
                "Pragma",
            };
            for (String header : allowedResponseHeaders) {
                headers.addAll(header, response.headers().allValues(header));
            }
            return ResponseEntity.status(response.statusCode()).headers(headers).body(body);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("Bad Gateway");
        }
    }
}
