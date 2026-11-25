/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import org.tailormap.api.persistence.Application;
import org.tailormap.api.persistence.GeoService;
import org.tailormap.api.persistence.json.GeoServiceLayer;
import org.tailormap.api.persistence.json.GeoServiceProtocol;
import org.tailormap.api.persistence.json.GeoServiceSettings;
import org.tailormap.api.security.AuthorisationService;

class GeoServiceProxyControllerTest {

  @Test
  void build_ogc_proxy_request_params() {
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

  private static final String LAYER_NAME = "layer";

  private static GeoServiceProxyController controller(Duration requestTimeout) {
    return new GeoServiceProxyController(mock(AuthorisationService.class), Duration.ofSeconds(5), requestTimeout);
  }

  private static GeoService wmsService(String url) {
    return new GeoService()
        .setId("test-wms")
        .setProtocol(GeoServiceProtocol.WMS)
        .setUrl(url)
        .setSettings(new GeoServiceSettings().useProxy(true));
  }

  private static GeoServiceLayer layer() {
    return new GeoServiceLayer()
        .id("0")
        .name(LAYER_NAME)
        .root(true)
        .styles(new ArrayList<>())
        .children(new ArrayList<>());
  }

  private static MockHttpServletRequest getMapRequest() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/proxy/wms");
    request.addParameter("REQUEST", "GetMap");
    request.addParameter("LAYERS", LAYER_NAME);
    return request;
  }

  private static ResponseStatusException proxyError(
      GeoServiceProxyController controller,
      GeoService service,
      GeoServiceProtocol protocol,
      MockHttpServletRequest request) {
    return assertThrows(
        ResponseStatusException.class,
        () -> controller.proxy(new Application(), service, layer(), protocol, request));
  }

  @Test
  void proxy_success() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(new MockResponse.Builder()
          .addHeader("Content-Type", "image/png")
          .addHeader("Cache-Control", "max-age=60")
          .addHeader("X-Not-Passed-Through", "x")
          .body("png")
          .build());
      server.start();

      ResponseEntity<?> response = controller(Duration.ofSeconds(5))
          .proxy(
              new Application(),
              wmsService(server.url("/wms").toString()),
              layer(),
              GeoServiceProtocol.WMS,
              getMapRequest());

      assertEquals(HttpStatus.OK, response.getStatusCode());
      assertEquals("image/png", response.getHeaders().getFirst("Content-Type"));
      assertEquals("max-age=60", response.getHeaders().getFirst("Cache-Control"));
      assertFalse(response.getHeaders().containsHeader("X-Not-Passed-Through"));
      assertEquals("GetMap", server.takeRequest().getUrl().queryParameter("REQUEST"));
    }
  }

  @Test
  void connection_refused_is_bad_gateway_without_credentials_in_message() throws Exception {
    String url;
    try (MockWebServer server = new MockWebServer()) {
      server.start();
      url = server.url("/wms").toString() + "?token=secret";
    }
    // The server is closed now, so the connection is refused
    ResponseStatusException e =
        proxyError(controller(Duration.ofSeconds(5)), wmsService(url), GeoServiceProtocol.WMS, getMapRequest());
    assertEquals(HttpStatus.BAD_GATEWAY, e.getStatusCode());
    assertNotNull(e.getReason());
    assertTrue(e.getReason().startsWith("Error proxying request to service at http://"), e.getReason());
    assertFalse(e.getReason().contains("secret"), e.getReason());
    assertFalse(e.getReason().contains("?"), e.getReason());
  }

  @Test
  void slow_service_is_gateway_timeout() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(new MockResponse.Builder()
          .headersDelay(5, TimeUnit.SECONDS)
          .body("too late")
          .build());
      server.start();

      ResponseStatusException e = proxyError(
          controller(Duration.ofMillis(200)),
          wmsService(server.url("/wms").toString()),
          GeoServiceProtocol.WMS,
          getMapRequest());
      assertEquals(HttpStatus.GATEWAY_TIMEOUT, e.getStatusCode());
      assertNotNull(e.getReason());
      assertTrue(e.getReason().startsWith("Timeout proxying request to service at "), e.getReason());
    }
  }

  @Test
  void interrupted_proxy_request_keeps_interrupt_flag() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(new MockResponse.Builder()
          .headersDelay(5, TimeUnit.SECONDS)
          .body("too late")
          .build());
      server.start();

      Thread.currentThread().interrupt();
      try {
        ResponseStatusException e = proxyError(
            controller(Duration.ofSeconds(10)),
            wmsService(server.url("/wms").toString()),
            GeoServiceProtocol.WMS,
            getMapRequest());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatusCode());
      } finally {
        // Also clears the flag for other tests
        assertTrue(Thread.interrupted(), "interrupt flag should be set again");
      }
    }
  }

  @Test
  void invalid_request_header_is_bad_request() {
    MockHttpServletRequest request = getMapRequest();
    request.addHeader("Referer", "http://example.com/\nX-Injected: true");
    ResponseStatusException e = proxyError(
        controller(Duration.ofSeconds(5)), wmsService("http://localhost/wms"), GeoServiceProtocol.WMS, request);
    assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
  }

  @Test
  void invalid_service_url_is_reported() {
    ResponseStatusException e = proxyError(
        controller(Duration.ofSeconds(5)),
        wmsService("http://localhost/wms?param=a b"),
        GeoServiceProtocol.WMS,
        getMapRequest());
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, e.getStatusCode());
    assertNotNull(e.getReason());
    assertTrue(e.getReason().startsWith("Invalid URL for proxying layer \"layer\""), e.getReason());
  }

  @Test
  void no_legend_url_is_not_found() {
    ResponseStatusException e = proxyError(
        controller(Duration.ofSeconds(5)),
        wmsService("http://localhost/wms"),
        GeoServiceProtocol.LEGEND,
        new MockHttpServletRequest("GET", "/proxy/legend"));
    assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
    assertEquals("No legend URL for layer \"layer\"", e.getReason());
  }

  @Test
  void not_modified_when_service_ignores_conditional_request() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(new MockResponse.Builder()
          .addHeader("Content-Type", "image/png")
          .addHeader("ETag", "\"tile-1\"")
          .addHeader("Cache-Control", "max-age=60")
          .body("png")
          .build());
      server.start();

      MockHttpServletRequest request = getMapRequest();
      request.addHeader("If-None-Match", "\"tile-1\"");
      ResponseEntity<?> response = controller(Duration.ofSeconds(5))
          .proxy(
              new Application(),
              wmsService(server.url("/wms").toString()),
              layer(),
              GeoServiceProtocol.WMS,
              request);

      assertEquals(HttpStatus.NOT_MODIFIED, response.getStatusCode());
      assertEquals("\"tile-1\"", response.getHeaders().getETag());
      assertEquals("max-age=60", response.getHeaders().getFirst("Cache-Control"));
      assertNull(response.getBody());
      assertEquals("\"tile-1\"", server.takeRequest().getHeaders().get("If-None-Match"));
    }
  }

  @Test
  void request_param_name_with_special_characters_is_encoded() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(new MockResponse.Builder().body("ok").build());
      server.start();

      MockHttpServletRequest request = getMapRequest();
      request.addParameter("vendor param", "a b+c");
      ResponseEntity<?> response = controller(Duration.ofSeconds(5))
          .proxy(
              new Application(),
              wmsService(server.url("/wms").toString()),
              layer(),
              GeoServiceProtocol.WMS,
              request);

      assertEquals(HttpStatus.OK, response.getStatusCode());
      assertEquals("a b+c", server.takeRequest().getUrl().queryParameter("vendor param"));
    }
  }

  @Test
  void uri_for_logging_has_no_user_info_or_query() {
    assertEquals(
        "https://example.com:8443/geoserver/wms",
        GeoServiceProxyController.withoutUserInfoAndQuery(
            URI.create("https://user:pass@example.com:8443/geoserver/wms?apikey=secret#frag")));
  }
}
