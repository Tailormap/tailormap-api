/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

class GeoServiceProxyControllerTest {

  @Test
  void buildOgcProxyRequestParams() {
    String originalUrl = "https://service/path1?param=abc";
    String requestUrl = "https://api/path2?request=GetMap&other=value";
    assertEquals(
        UriComponentsBuilder.fromUriString("https://service/path1?request=GetMap&param=abc&other=value")
            .build(),
        getProxyUrl(originalUrl, requestUrl),
        "bad proxy params");

    originalUrl = "https://service/path1?param=abc&REQUEST=GetCapabilities&original=value";
    requestUrl = "https://api/path2?request=GetMap&param=xyz";
    assertEquals(
        UriComponentsBuilder.fromUriString("https://service/path1?request=GetMap&param=xyz&original=value")
            .build(),
        getProxyUrl(originalUrl, requestUrl),
        "bad proxy params");
  }

  private static UriComponents getProxyUrl(String originalUrl, String requestUrl) {
    UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromUriString(originalUrl);
    uriComponentsBuilder.replaceQueryParams(GeoServiceProxyController.buildOgcProxyRequestParams(
        getUrlParams(originalUrl), getUrlParams(requestUrl)));
    return uriComponentsBuilder.build();
  }

  private static MultiValueMap<String, String> getUrlParams(String url) {
    return UriComponentsBuilder.fromUriString(url).build().getQueryParams();
  }
}
