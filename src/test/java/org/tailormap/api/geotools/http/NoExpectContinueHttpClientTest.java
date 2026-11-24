/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.geotools.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import mockwebserver3.junit5.StartStop;
import org.geotools.http.HTTPClient;
import org.geotools.http.HTTPClientFinder;
import org.geotools.http.HTTPConnectionPooling;
import org.geotools.http.HTTPProxy;
import org.geotools.http.LoggingHTTPClient;
import org.geotools.http.SimpleHttpClient;
import org.geotools.http.commons.MultithreadedHttpClient;
import org.geotools.util.factory.Hints;
import org.junit.jupiter.api.Test;

class NoExpectContinueHttpClientTest {
  private static final String BODY = "<wfs:GetFeature xmlns:wfs=\"http://www.opengis.net/wfs/2.0\"/>";

  @StartStop
  private final MockWebServer server = new MockWebServer();

  private RecordedRequest post(HTTPClient client) throws IOException, InterruptedException {
    client.post(
            server.url("/wfs").url(),
            new ByteArrayInputStream(BODY.getBytes(StandardCharsets.UTF_8)),
            "text/xml")
        .dispose();
    RecordedRequest request = server.takeRequest(10, TimeUnit.SECONDS);
    assertNotNull(request);
    return request;
  }

  private static final Hints FACTORY_HINT =
      new Hints(Hints.HTTP_CLIENT_FACTORY, NoExpectContinueHttpClientFactory.class);

  /** Without the hint, GeoTools uses SimpleHttpClient when no behaviors are requested, keep doing that. */
  @Test
  void factory_hint_creates_simple_client() {
    assertInstanceOf(SimpleHttpClient.class, HTTPClientFinder.createClient(FACTORY_HINT));
  }

  @Test
  void factory_creates_client_for_behaviors() {
    NoExpectContinueHttpClientFactory factory = new NoExpectContinueHttpClientFactory();
    assertInstanceOf(SimpleHttpClient.class, factory.createClient(List.of()));
    assertInstanceOf(SimpleHttpClient.class, factory.createClient(List.of(HTTPProxy.class)));
    assertInstanceOf(NoExpectContinueHttpClient.class, factory.createClient(List.of(HTTPConnectionPooling.class)));
    assertInstanceOf(
        NoExpectContinueHttpClient.class,
        factory.createClient(List.of(HTTPConnectionPooling.class, HTTPProxy.class)));
  }

  @Test
  void logging_client_keeps_connection_pooling() {
    NoExpectContinueHttpClientFactory factory = new NoExpectContinueHttpClientFactory();
    Hints hints = new Hints(Hints.HTTP_LOGGING, "true");
    assertInstanceOf(
        HTTPConnectionPooling.class, factory.createClient(hints, List.of(HTTPConnectionPooling.class)));
    HTTPClient simple = factory.createClient(hints, List.of());
    assertInstanceOf(LoggingHTTPClient.class, simple);
    assertFalse(simple instanceof HTTPConnectionPooling);
  }

  @Test
  void simple_client_post_does_not_send_expect_header() throws Exception {
    server.enqueue(new MockResponse.Builder().body("ok").build());
    assertNull(post(new SimpleHttpClient()).getHeaders().get("Expect"));
  }

  @Test
  void post_does_not_send_expect_header() throws Exception {
    server.enqueue(new MockResponse.Builder().body("ok").build());
    try (NoExpectContinueHttpClient client = new NoExpectContinueHttpClient()) {
      RecordedRequest request = post(client);
      assertNull(request.getHeaders().get("Expect"));
      assertNotNull(request.getBody());
      assertEquals(BODY, request.getBody().utf8());
    }
  }

  /**
   * Checks the GeoTools client still sends the header, so {@link NoExpectContinueHttpClient} is still needed. When
   * this fails after a GeoTools upgrade, the workaround can be removed.
   */
  @Test
  void geotools_client_sends_expect_header() throws Exception {
    // Respond with "100 Continue" right away, so the client doesn't wait for its timeout
    server.enqueue(new MockResponse.Builder()
        .addInformationalResponse(new MockResponse.Builder().code(100).build())
        .body("ok")
        .build());
    try (MultithreadedHttpClient client = new MultithreadedHttpClient()) {
      assertEquals("100-continue", post(client).getHeaders().get("Expect"));
    }
  }

  /**
   * {@link MultithreadedHttpClient#setConnectTimeout(int)} doesn't change the timeout of the connection manager, so
   * connecting to a non-routable address would take the initial 30 seconds. If the network rejects the address right
   * away this passes without testing anything, but it doesn't fail.
   */
  @Test
  void connect_timeout_is_applied() {
    try (NoExpectContinueHttpClient client = new NoExpectContinueHttpClient()) {
      client.setConnectTimeout(1);
      assertEquals(1, client.getConnectTimeout());
      assertTimeoutPreemptively(
          Duration.ofSeconds(10),
          () -> assertThrows(
              IOException.class,
              () -> client.get(URI.create("http://10.255.255.1/").toURL())));
    }
  }
}
