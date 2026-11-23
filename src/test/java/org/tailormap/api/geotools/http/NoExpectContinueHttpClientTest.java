/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.geotools.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import mockwebserver3.junit5.StartStop;
import org.geotools.http.HTTPClient;
import org.geotools.http.HTTPClientFinder;
import org.geotools.http.HTTPConnectionPooling;
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

  @Test
  void factory_hint_creates_no_expect_continue_client() {
    HTTPClient client = HTTPClientFinder.createClient(
        new Hints(Hints.HTTP_CLIENT_FACTORY, NoExpectContinueHttpClientFactory.class));
    assertInstanceOf(NoExpectContinueHttpClient.class, client);
    assertInstanceOf(HTTPConnectionPooling.class, client);
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
}
