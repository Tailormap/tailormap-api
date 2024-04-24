/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.util;

import jakarta.servlet.http.HttpServletRequest;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;
import org.springframework.http.HttpHeaders;

public class HttpProxyUtil {
  public static void addForwardedForRequestHeaders(
      HttpRequest.Builder requestBuilder, HttpServletRequest request) {
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
      String encoded =
          Base64.getEncoder().encodeToString(toEncode.getBytes(StandardCharsets.UTF_8));
      requestBuilder.header("Authorization", "Basic " + encoded);
    }
  }
}
