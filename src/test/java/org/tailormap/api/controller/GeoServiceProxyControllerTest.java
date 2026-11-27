/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import org.tailormap.api.persistence.Application;
import org.tailormap.api.persistence.GeoService;
import org.tailormap.api.persistence.json.GeoServiceLayer;
import org.tailormap.api.persistence.json.GeoServiceProtocol;
import org.tailormap.api.persistence.json.GeoServiceSettings;
import org.tailormap.api.persistence.json.ServiceAuthentication;
import org.tailormap.api.security.AuthorisationService;

class GeoServiceProxyControllerTest {
  private static final String LAYER_NAME = "postgis:begroeidterreindeel";
  private static final String PASSTHROUGH_LAYER_NAME =
      "vw_t_gi_postgis:begroeidterreindeel_70cae9814c6144808f1c9bb921099794";

  private GeoServiceProxyController controller;
  private final GeoService service = new GeoService().setUrl("https://snapshot.tailormap.nl/geoserver/wms");
  private final GeoServiceLayer layer = new GeoServiceLayer().name(LAYER_NAME);

  @BeforeEach
  void setUp() {
    controller = new GeoServiceProxyController(null, Duration.ofSeconds(10), Duration.ofSeconds(60));
  }

  private static Map<String, String[]> params(String... keyValues) {
    Map<String, String[]> params = new LinkedHashMap<>();
    for (int i = 0; i < keyValues.length; i += 2) {
      String key = keyValues[i];
      String value = keyValues[i + 1];
      params.merge(key, new String[] {value}, (a, b) -> {
        String[] merged = new String[a.length + b.length];
        System.arraycopy(a, 0, merged, 0, a.length);
        System.arraycopy(b, 0, merged, a.length, b.length);
        return merged;
      });
    }
    return params;
  }

  private void assertAllowed(GeoServiceProtocol protocol, Map<String, String[]> params) {
    assertDoesNotThrow(() -> controller.checkOgcRequestParams(service, layer, protocol, params));
  }

  private void assertDenied(GeoServiceProtocol protocol, Map<String, String[]> params, String expectedMessage) {
    ResponseStatusException e = assertThrows(
        ResponseStatusException.class,
        () -> controller.checkOgcRequestParams(service, layer, protocol, params));
    assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
    assertEquals(expectedMessage, e.getReason());
  }

  private static Map<String, String[]> viewerGetMap(String layers) {
    // As requested by OpenLayers ImageWMS/TileWMS in the viewer
    return params(
        "SERVICE",
        "WMS",
        "VERSION",
        "1.3.0",
        "REQUEST",
        "GetMap",
        "FORMAT",
        "image/png",
        "TRANSPARENT",
        "TRUE",
        "LAYERS",
        layers,
        "STYLES",
        "",
        "WIDTH",
        "256",
        "HEIGHT",
        "256",
        "CRS",
        "EPSG:28992",
        "BBOX",
        "0,0,1000,1000");
  }

  @Test
  void wms_get_map_from_viewer_allowed() {
    assertAllowed(GeoServiceProtocol.WMS, viewerGetMap(LAYER_NAME));
  }

  @Test
  void wms_get_capabilities_allowed() {
    assertAllowed(GeoServiceProtocol.WMS, params("SERVICE", "WMS", "REQUEST", "GetCapabilities"));
    assertAllowed(GeoServiceProtocol.WMS, params("request", "getcapabilities", "VERSION", "1.1.1"));
  }

  @Test
  void wms_get_map_other_layer_denied() {
    assertDenied(
        GeoServiceProtocol.WMS,
        viewerGetMap("postgis:other_layer"),
        "Requested layer name does not match expected layer");
  }

  @Test
  void wms_get_map_without_layers_denied() {
    assertDenied(
        GeoServiceProtocol.WMS,
        params("SERVICE", "WMS", "REQUEST", "GetMap", "FORMAT", "image/png"),
        "Missing LAYERS parameter");
  }

  @Test
  void wms_get_map_lowercase_param_names() {
    assertAllowed(GeoServiceProtocol.WMS, params("service", "wms", "request", "getmap", "layers", LAYER_NAME));
    assertDenied(
        GeoServiceProtocol.WMS,
        params("service", "wms", "request", "getmap", "layers", "other"),
        "Requested layer name does not match expected layer");
  }

  @Test
  void wms_multiple_layers_denied() {
    assertDenied(
        GeoServiceProtocol.WMS,
        viewerGetMap(LAYER_NAME + ",postgis:other_layer"),
        "Multiple layers in LAYERS parameter not supported");
    assertDenied(
        GeoServiceProtocol.WMS,
        params("REQUEST", "GetMap", "LAYERS", LAYER_NAME, "layers", "postgis:other_layer"),
        "Multiple layers in LAYERS parameter not supported");
    assertDenied(
        GeoServiceProtocol.WMS,
        params("REQUEST", "GetMap", "LAYERS", LAYER_NAME, "LAYERS", "postgis:other_layer"),
        "Multiple layers in LAYERS parameter not supported");
  }

  @Test
  void wms_layer_param_on_get_map_checked() {
    Map<String, String[]> params = viewerGetMap(LAYER_NAME);
    params.put("LAYER", new String[] {"postgis:other_layer"});
    assertDenied(GeoServiceProtocol.WMS, params, "Requested layer name does not match expected layer");
  }

  @Test
  void wms_get_feature_info_from_viewer_allowed() {
    Map<String, String[]> params = viewerGetMap(LAYER_NAME);
    params.put("REQUEST", new String[] {"GetFeatureInfo"});
    params.put("QUERY_LAYERS", new String[] {LAYER_NAME});
    params.put("INFO_FORMAT", new String[] {"application/json"});
    params.put("FEATURE_COUNT", new String[] {"10"});
    params.put("I", new String[] {"50"});
    params.put("J", new String[] {"50"});
    assertAllowed(GeoServiceProtocol.WMS, params);
  }

  @Test
  void wms_get_feature_info_other_query_layer_denied() {
    Map<String, String[]> params = viewerGetMap(LAYER_NAME);
    params.put("REQUEST", new String[] {"GetFeatureInfo"});
    params.put("QUERY_LAYERS", new String[] {"postgis:other_layer"});
    assertDenied(GeoServiceProtocol.WMS, params, "Requested layer name does not match expected layer");

    params.put("QUERY_LAYERS", new String[] {LAYER_NAME + ",postgis:other_layer"});
    assertDenied(GeoServiceProtocol.WMS, params, "Multiple layers in QUERY_LAYERS parameter not supported");

    params.remove("QUERY_LAYERS");
    assertDenied(GeoServiceProtocol.WMS, params, "Missing QUERY_LAYERS parameter");
  }

  @Test
  void wms_get_legend_graphic_from_viewer_allowed() {
    // As requested by OpenLayers ImageWMS.getLegendUrl() in the viewer
    assertAllowed(
        GeoServiceProtocol.WMS,
        params(
            "SERVICE",
            "WMS",
            "VERSION",
            "1.1.0",
            "REQUEST",
            "GetLegendGraphic",
            "FORMAT",
            "image/png",
            "LAYER",
            LAYER_NAME,
            "SLD_VERSION",
            "1.1.0",
            "SCALE",
            "1000"));
  }

  @Test
  void wms_get_legend_graphic_other_layer_denied() {
    assertDenied(
        GeoServiceProtocol.WMS,
        params("SERVICE", "WMS", "REQUEST", "GetLegendGraphic", "LAYER", "postgis:other_layer"),
        "Requested layer name does not match expected layer");
    assertDenied(
        GeoServiceProtocol.WMS,
        params("SERVICE", "WMS", "REQUEST", "GetLegendGraphic"),
        "Missing LAYER parameter");
  }

  @Test
  void wms_sld_params_denied() {
    for (String sldParam : new String[] {"SLD", "SLD_BODY", "SLD_URL"}) {
      Map<String, String[]> params = viewerGetMap(LAYER_NAME);
      params.put(sldParam.toLowerCase(Locale.ROOT), new String[] {"<StyledLayerDescriptor/>"});
      assertDenied(GeoServiceProtocol.WMS, params, sldParam + " parameter not allowed for proxied services");
    }
  }

  @Test
  void unsupported_request_denied() {
    assertDenied(
        GeoServiceProtocol.WMS,
        params("SERVICE", "WMS", "VERSION", "1.1.1", "LAYERS", LAYER_NAME),
        "Missing or unsupported REQUEST parameter for proxy protocol");
    assertDenied(
        GeoServiceProtocol.WMS,
        params("REQUEST", "DescribeLayer", "LAYERS", LAYER_NAME),
        "Missing or unsupported REQUEST parameter for proxy protocol");
    assertDenied(
        GeoServiceProtocol.WMTS,
        params("REQUEST", "GetMap", "LAYERS", LAYER_NAME),
        "Missing or unsupported REQUEST parameter for proxy protocol");
    assertDenied(
        GeoServiceProtocol.WMS,
        params("REQUEST", "GetCapabilities", "request", "GetMap"),
        "Multiple REQUEST parameters not supported");
  }

  @Test
  void other_service_denied() {
    // For example when the service URL is a GeoServer OWS endpoint
    assertDenied(
        GeoServiceProtocol.WMS,
        params("SERVICE", "WFS", "REQUEST", "GetCapabilities"),
        "Invalid SERVICE parameter for proxy protocol");
    assertDenied(
        GeoServiceProtocol.WMS,
        params("SERVICE", "WFS", "REQUEST", "GetFeature", "TYPENAMES", "postgis:other_layer"),
        "Invalid SERVICE parameter for proxy protocol");
    assertDenied(
        GeoServiceProtocol.WMS,
        params("REQUEST", "GetFeature", "TYPENAMES", "postgis:other_layer"),
        "Missing or unsupported REQUEST parameter for proxy protocol");
  }

  private static Map<String, String[]> viewerGetTile(String layer) {
    // As requested by OpenLayers WMTS source with KVP request encoding in the viewer
    return params(
        "layer",
        layer,
        "style",
        "default",
        "tilematrixset",
        "rd",
        "Service",
        "WMTS",
        "Request",
        "GetTile",
        "Version",
        "1.0.0",
        "Format",
        "image/png",
        "TileMatrix",
        "4",
        "TileCol",
        "8",
        "TileRow",
        "7");
  }

  @Test
  void wmts_get_tile_from_viewer_allowed() {
    assertAllowed(GeoServiceProtocol.WMTS, viewerGetTile(LAYER_NAME));
  }

  @Test
  void wmts_get_capabilities_from_viewer_allowed() {
    assertAllowed(GeoServiceProtocol.WMTS, params("REQUEST", "GetCapabilities", "SERVICE", "WMTS"));
  }

  @Test
  void wmts_get_tile_other_layer_denied() {
    assertDenied(
        GeoServiceProtocol.WMTS,
        viewerGetTile("other_layer"),
        "Requested layer name does not match expected layer");
  }

  @Test
  void wmts_get_tile_without_layer_denied() {
    Map<String, String[]> params = viewerGetTile(LAYER_NAME);
    params.remove("layer");
    assertDenied(GeoServiceProtocol.WMTS, params, "Missing LAYER parameter");
  }

  @Test
  void wmts_get_feature_info_other_layer_denied() {
    Map<String, String[]> params = viewerGetTile("other_layer");
    params.put("Request", new String[] {"GetFeatureInfo"});
    assertDenied(GeoServiceProtocol.WMTS, params, "Requested layer name does not match expected layer");
  }

  @Test
  void wmts_service_param_must_match() {
    Map<String, String[]> params = viewerGetTile(LAYER_NAME);
    params.put("Service", new String[] {"WMS"});
    assertDenied(GeoServiceProtocol.WMTS, params, "Invalid SERVICE parameter for proxy protocol");
  }

  @Test
  void passthrough_layer_patterns() {
    Map<String, String[]> getFeatureInfo = viewerGetMap(PASSTHROUGH_LAYER_NAME);
    getFeatureInfo.put("REQUEST", new String[] {"GetFeatureInfo"});
    getFeatureInfo.put("QUERY_LAYERS", new String[] {PASSTHROUGH_LAYER_NAME});
    Map<String, String[]> getLegendGraphic = params("REQUEST", "GetLegendGraphic", "LAYER", PASSTHROUGH_LAYER_NAME);

    // Not allowed without configuration
    assertDenied(
        GeoServiceProtocol.WMS,
        viewerGetMap(PASSTHROUGH_LAYER_NAME),
        "Requested layer name does not match expected layer");

    ReflectionTestUtils.setField(
        controller, "proxyLayerPassthroughPatterns", Set.of("vw_t_gi_%s_[a-fA-F0-9]{32}", "test_%s_.*"));
    assertAllowed(GeoServiceProtocol.WMS, viewerGetMap(PASSTHROUGH_LAYER_NAME));
    assertAllowed(GeoServiceProtocol.WMS, getFeatureInfo);
    assertAllowed(GeoServiceProtocol.WMS, getLegendGraphic);
    assertDenied(
        GeoServiceProtocol.WMS,
        viewerGetMap("vw_t_gi_postgis:other_layer_70cae9814c6144808f1c9bb921099794"),
        "Requested layer name does not match expected layer");

    ReflectionTestUtils.setField(
        controller, "proxyPassthroughHostNames", Set.of("localhost", "SNAPSHOT.tailormap.nl"));
    assertAllowed(GeoServiceProtocol.WMS, getFeatureInfo);

    ReflectionTestUtils.setField(controller, "proxyPassthroughHostNames", Set.of("localhost"));
    assertDenied(
        GeoServiceProtocol.WMS,
        getFeatureInfo,
        "Requested service hostname does not match allowed hostnames for layer passthrough");
    // The hostname is only checked for passthrough layer names
    assertAllowed(GeoServiceProtocol.WMS, viewerGetMap(LAYER_NAME));
  }

  @Test
  void legend_request_params() {
    // As requested by the viewer for a legendImageUrl with a proxied legend
    assertDoesNotThrow(() -> GeoServiceProxyController.checkLegendRequestParams(params(
        "STYLE",
        "purple_polygon",
        "SCALE",
        "1000",
        "LANGUAGE",
        "nl",
        "LEGEND_OPTIONS",
        "fontAntiAliasing:true")));

    for (String param : new String[] {"LAYER", "layer", "LAYERS", "QUERY_LAYERS", "SLD", "SLD_BODY", "sld_url"}) {
      ResponseStatusException e = assertThrows(
          ResponseStatusException.class,
          () -> GeoServiceProxyController.checkLegendRequestParams(params(param, "postgis:other_layer")));
      assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
    }
  }

  @Test
  void add_legend_request_params() {
    URI legendURI = URI.create("https://example.com/geoserver/wms?service=WMS&request=GetLegendGraphic"
        + "&format=image%2Fpng&width=20&height=20&layer=postgis%3Abegroeidterreindeel&style=green");
    URI result = GeoServiceProxyController.addLegendRequestParams(
        legendURI,
        params(
            "STYLE",
            "purple polygon",
            "SCALE",
            "1000",
            "SERVICE",
            "WFS",
            "REQUEST",
            "GetFeature",
            "LANGUAGE",
            "nl"));
    assertEquals(
        URI.create("https://example.com/geoserver/wms?service=WMS&request=GetLegendGraphic&format=image%2Fpng"
            + "&width=20&height=20&layer=postgis%3Abegroeidterreindeel&STYLE=purple%20polygon&SCALE=1000"
            + "&LANGUAGE=nl"),
        result);
  }

  @Test
  void build_ogc_proxy_request_params() {
    String originalUrl = "https://service/path1?param=abc";
    String requestUrl = "https://api/path2?request=GetMap&other=value";
    assertEquals(
        UriComponentsBuilder.fromUriString("https://service/path1?request=GetMap&other=value&param=abc")
            .build(),
        getProxyUrl(originalUrl, requestUrl),
        "bad proxy params");

    // Service URL parameters can't be overridden by the request, except the OGC parameters
    originalUrl = "https://service/path1?param=abc&REQUEST=GetCapabilities&original=value";
    requestUrl = "https://api/path2?request=GetMap&PARAM=xyz";
    assertEquals(
        UriComponentsBuilder.fromUriString("https://service/path1?request=GetMap&param=abc&original=value")
            .build(),
        getProxyUrl(originalUrl, requestUrl),
        "bad proxy params");

    originalUrl = "https://service/cgi-bin/mapserv?map=/maps/public.map";
    requestUrl = "https://api/path2?REQUEST=GetMap&MAP=/maps/secret.map";
    assertEquals(
        UriComponentsBuilder.fromUriString(
                "https://service/cgi-bin/mapserv?REQUEST=GetMap&map=/maps/public.map")
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

  private static final String PROXIED_LAYER_NAME = "layer";

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
        .name(PROXIED_LAYER_NAME)
        .root(true)
        .styles(new ArrayList<>())
        .children(new ArrayList<>());
  }

  private static MockHttpServletRequest getMapRequest() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/proxy/wms");
    request.addParameter("REQUEST", "GetMap");
    request.addParameter("LAYERS", PROXIED_LAYER_NAME);
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
  void upstream_unauthorized_is_bad_gateway() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(new MockResponse.Builder()
          .code(401)
          .addHeader("WWW-Authenticate", "Basic realm=\"geoserver\"")
          .body("Unauthorized")
          .build());
      server.start();

      GeoService service = wmsService(server.url("/wms").toString())
          .setAuthentication(new ServiceAuthentication()
              .method(ServiceAuthentication.MethodEnum.PASSWORD)
              .username("user")
              .password("wrong"));
      ResponseStatusException e =
          proxyError(controller(Duration.ofSeconds(5)), service, GeoServiceProtocol.WMS, getMapRequest());
      assertEquals(HttpStatus.BAD_GATEWAY, e.getStatusCode());
      assertEquals("Proxied service test-wms did not accept the configured credentials", e.getReason());
      assertNotNull(server.takeRequest().getHeaders().get("Authorization"));
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
