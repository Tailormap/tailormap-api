/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import io.swagger.v3.oas.annotations.Parameter;

import nl.b3p.tailormap.api.model.ErrorResponse;
import nl.b3p.tailormap.api.model.RedirectResponse;
import nl.b3p.tailormap.api.repository.ApplicationLayerRepository;
import nl.b3p.tailormap.api.repository.ApplicationRepository;
import nl.b3p.tailormap.api.security.AuthorizationService;
import nl.tailormap.viewer.config.app.Application;
import nl.tailormap.viewer.config.app.ApplicationLayer;
import nl.tailormap.viewer.config.services.GeoService;
import nl.tailormap.viewer.config.services.TileService;
import nl.tailormap.viewer.config.services.WMSService;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.InputStream;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import javax.persistence.EntityNotFoundException;
import javax.servlet.http.HttpServletRequest;

@RestController
@Validated
@RequestMapping(path = "/app/{appId}/layer/{appLayerId}/proxy")
@SuppressWarnings("unused")
public class GeoServiceProxyController {

    private final Log logger = LogFactory.getLog(getClass());
    private final ApplicationRepository applicationRepository;
    private final ApplicationLayerRepository applicationLayerRepository;
    private final AuthorizationService authorizationService;

    @Autowired
    public GeoServiceProxyController(
            ApplicationRepository applicationRepository,
            ApplicationLayerRepository applicationLayerRepository,
            AuthorizationService authorizationService) {
        this.applicationRepository = applicationRepository;
        this.applicationLayerRepository = applicationLayerRepository;
        this.authorizationService = authorizationService;
    }

    @ExceptionHandler(EntityNotFoundException.class)
    @ResponseStatus(
            value =
                    HttpStatus
                            .NOT_FOUND /*,reason = "Not Found" -- adding 'reason' will drop the body */)
    @ResponseBody
    public ErrorResponse handleEntityNotFoundException(EntityNotFoundException exception) {
        logger.warn(
                "Requested an application or appLayer that does not exist. Message: "
                        + exception.getMessage());
        return new ErrorResponse()
                .message("Requested an application or appLayer that does not exist")
                .code(HttpStatus.NOT_FOUND.value());
    }

    @GetMapping(path = "/wmts")
    public ResponseEntity<?> proxyWmts(
            @Parameter(name = "appId", description = "application id", required = true)
                    @PathVariable("appId")
                    Long appId,
            @Parameter(name = "appLayerId", description = "application layer id", required = true)
                    @PathVariable("appLayerId")
                    Long appLayerId,
            HttpServletRequest request) {
        return proxy(
                (GeoService gs) ->
                        TileService.PROTOCOL.equals(gs.getProtocol())
                                && TileService.TILING_PROTOCOL_WMTS.equals(
                                        ((TileService) gs).getTilingProtocol()),
                appId,
                appLayerId,
                request);
    }

    @GetMapping(path = "/wms")
    public ResponseEntity<?> proxyWms(
            @Parameter(name = "appId", description = "application id", required = true)
                    @PathVariable("appId")
                    Long appId,
            @Parameter(name = "appLayerId", description = "application layer id", required = true)
                    @PathVariable("appLayerId")
                    Long appLayerId,
            HttpServletRequest request) {
        return proxy(
                (GeoService gs) -> WMSService.PROTOCOL.equals(gs.getProtocol()),
                appId,
                appLayerId,
                request);
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

    private ResponseEntity<?> proxy(
            Predicate<GeoService> serviceValidator,
            Long appId,
            Long appLayerId,
            HttpServletRequest request) {

        final Application application = applicationRepository.findById(appId).orElseThrow();
        final ApplicationLayer appLayer =
                applicationLayerRepository.findById(appLayerId).orElseThrow();
        if (!authorizationService.mayUserRead(appLayer, application)) {
            // login required, send RedirectResponse
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new RedirectResponse());
        } else {
            final GeoService service = appLayer.getService();

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
            final MultiValueMap<String, String> requestParams =
                    UriComponentsBuilder.fromHttpRequest(new ServletServerHttpRequest(request))
                            .build(true)
                            .getQueryParams();
            final MultiValueMap<String, String> params =
                    buildOgcProxyRequestParams(
                            originalServiceUrl.build(true).getQueryParams(), requestParams);
            originalServiceUrl.replaceQueryParams(params);

            return doProxy(originalServiceUrl.build(true).toUri(), service, request);
        }
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

        // TODO: add request headers such as conditional HTTP requests

        if (service.getUsername() != null && service.getPassword() != null) {
            String toEncode = service.getUsername() + ":" + service.getPassword();
            requestBuilder.header(
                    "Authorization",
                    "Basic "
                            + Base64.getEncoder()
                                    .encodeToString(toEncode.getBytes(StandardCharsets.UTF_8)));
        }

        try {
            HttpResponse<InputStream> response =
                    httpClient.send(
                            requestBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());
            InputStreamResource body = new InputStreamResource(response.body());
            org.springframework.http.HttpHeaders headers = new HttpHeaders();
            // TODO for WMTS, does caching work?
            String[] forwardHeaders = {
                "Content-Type", "Content-Length", "Cache-Control", "Expires", "Pragma"
            };
            for (String header : forwardHeaders) {
                headers.addAll(header, response.headers().allValues(header));
            }
            return ResponseEntity.status(response.statusCode()).headers(headers).body(body);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("Bad Gateway");
        }
    }
}
