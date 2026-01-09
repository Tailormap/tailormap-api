/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.util;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

import jakarta.servlet.http.HttpServletRequest;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

public class HttpProxyUtil {
  public static void addForwardedForRequestHeaders(HttpRequest.Builder requestBuilder, HttpServletRequest request) {
    try {
      String ip = request.getRemoteAddr();
      InetAddress inetAddress = InetAddress.getByName(ip);

      if (inetAddress instanceof Inet6Address inet6Address) {
        // https://stackoverflow.com/questions/33168783/
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
  }

  public static void passthroughRequestHeaders(
      HttpRequest.Builder requestBuilder, HttpServletRequest request, Set<String> headers) {
    for (String header : headers) {
      String value = request.getHeader(header);
      if (value != null) {
        requestBuilder.header(header, value);
      }
    }
  }

  public static HttpHeaders passthroughResponseHeaders(
      java.net.http.HttpHeaders upstreamHeaders, Set<String> allowedResponseHeaders) {
    HttpHeaders headers = new HttpHeaders();
    for (String header : allowedResponseHeaders) {
      headers.addAll(header, upstreamHeaders.allValues(header));
    }
    return headers;
  }

  public static void setHttpBasicAuthenticationHeader(
      HttpRequest.Builder requestBuilder, String username, String password) {
    if (username != null && password != null) {
      String toEncode = username + ":" + password;
      String encoded = Base64.getEncoder().encodeToString(toEncode.getBytes(StandardCharsets.UTF_8));
      requestBuilder.header("Authorization", "Basic " + encoded);
    }
  }

  /**
   * If the original request was a POST with x-www-urlencoded content type, configure the requestBuilder for a proxy
   * request to do a POST request with all parameters in the body to handle large POST parameters.
   *
   * @param requestBuilder builder for proxy request
   * @param uri URI of the proxy target, including query parameters
   * @param request the original request to be proxied
   */
  public static void configureProxyRequestBuilderForUri(
      HttpRequest.Builder requestBuilder, URI uri, HttpServletRequest request) {
    // When the original request is a POST with x-www-form-urlencoded content type, do the same (for long parameters
    // like CQL_FILTER which may trigger a URI Too Long or Bad Request response)
    if (HttpMethod.POST.matches(request.getMethod())
        && request.getContentType() != null
        && request.getContentType()
            .toLowerCase(Locale.ROOT)
            .contains(MediaType.APPLICATION_FORM_URLENCODED_VALUE)) {

      // The original request could have some parameters in the URL and some in the body, but in the proxied
      // request we put all parameters in the body
      // Make sure to not decode the query so '+' stays encoded as "%2B" and does not become a space
      String query = uri.getRawQuery();
      URI uriWithoutQuery = URI.create(uri.toString().split("\\?", 2)[0]);
      requestBuilder.uri(uriWithoutQuery);

      if (isNotEmpty(query)) {
        requestBuilder
            .POST(HttpRequest.BodyPublishers.ofString(query))
            .header("Content-Type", "application/x-www-form-urlencoded");
      } else {
        requestBuilder.uri(uri);
      }
    } else {
      requestBuilder.uri(uri);
    }
  }
}
