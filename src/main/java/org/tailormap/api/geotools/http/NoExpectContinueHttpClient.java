/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.geotools.http;

import java.lang.reflect.Field;
import org.apache.hc.client5.http.config.RequestConfig;
import org.geotools.http.commons.MultithreadedHttpClient;

/**
 * GeoTools {@link MultithreadedHttpClient} that doesn't send an {@code Expect: 100-continue} header with POST requests.
 *
 * <p>{@link MultithreadedHttpClient} enables the {@code Expect: 100-continue} handshake for all requests. With it, the
 * Apache HttpClient sends a POST request without its body first and waits for a {@code 100 Continue} response before
 * sending the body, or sends the body anyway after a timeout of 3 seconds. Some servers (for example the PDOK WFS
 * services at {@code service.pdok.nl}) only send the {@code 100 Continue} response after receiving the body, so every
 * POST request (WFS GetFeature, and DescribeFeatureType for WFS 1.1.0) takes at least 3 seconds. The handshake only
 * avoids sending a large body that the server rejects based on the headers alone, which doesn't matter for the small
 * XML request bodies GeoTools sends.
 *
 * <p>{@link MultithreadedHttpClient} doesn't allow changing its request configuration, so it is replaced using
 * reflection. If that fails because of a changed GeoTools implementation, the constructor throws an exception, so this
 * is caught by the unit test when upgrading GeoTools.
 */
public class NoExpectContinueHttpClient extends MultithreadedHttpClient {
  private static final String REQUEST_CONFIG_FIELD = "requestConfig";

  public NoExpectContinueHttpClient() {
    super();
    try {
      Field field = MultithreadedHttpClient.class.getDeclaredField(REQUEST_CONFIG_FIELD);
      field.setAccessible(true);
      RequestConfig requestConfig = (RequestConfig) field.get(this);
      field.set(
          this,
          RequestConfig.copy(requestConfig)
              .setExpectContinueEnabled(false)
              .build());
    } catch (ReflectiveOperationException | ClassCastException e) {
      throw new IllegalStateException(
          "Can't disable Expect: 100-continue for " + MultithreadedHttpClient.class.getName(), e);
    }
  }
}
