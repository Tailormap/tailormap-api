/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.geotools.wfs;

import static nl.b3p.tailormap.api.util.HttpProxyUtil.addForwardedForRequestHeaders;
import static nl.b3p.tailormap.api.util.HttpProxyUtil.passthroughRequestHeaders;
import static nl.b3p.tailormap.api.util.HttpProxyUtil.setHttpBasicAuthenticationHeader;

import jakarta.servlet.http.HttpServletRequest;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Set;

public class WFSProxy {
  public static HttpResponse<InputStream> proxyWfsRequest(
      URI wfsRequest, String username, String password, HttpServletRequest request)
      throws Exception {
    final HttpClient httpClient = HttpClient.newBuilder().build();

    HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().uri(wfsRequest);

    addForwardedForRequestHeaders(requestBuilder, request);

    // Just a few headers for logging, conditional or range requests not likely to be supported by a
    // WFS
    passthroughRequestHeaders(requestBuilder, request, Set.of("Referer", "User-Agent"));

    setHttpBasicAuthenticationHeader(requestBuilder, username, password);

    return httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());
  }
}
