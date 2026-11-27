/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.geotools.http;

import java.lang.reflect.Field;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
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
 * <p>Also fixes {@link #setConnectTimeout(int)}: {@link MultithreadedHttpClient} (GeoTools 35.2) only replaces its
 * connection config field, which the connection manager has already copied in the constructor, so the connect timeout
 * always stays at the initial 30 seconds.
 *
 * <p>{@link MultithreadedHttpClient} doesn't allow changing its request configuration or accessing its connection
 * manager, so this uses reflection. If that fails because of a changed GeoTools implementation, the constructor throws
 * an exception, so this is caught by the unit test when upgrading GeoTools.
 */
public class NoExpectContinueHttpClient extends MultithreadedHttpClient {
  private final Field connectionConfigField;
  private final PoolingHttpClientConnectionManager connectionManager;

  public NoExpectContinueHttpClient() {
    super();
    try {
      Field requestConfigField = getField("requestConfig");
      RequestConfig requestConfig = (RequestConfig) requestConfigField.get(this);
      requestConfigField.set(
          this,
          RequestConfig.copy(requestConfig)
              .setExpectContinueEnabled(false)
              .build());

      connectionConfigField = getField("connectionConfig");
      connectionManager = (PoolingHttpClientConnectionManager)
          getField("connectionManager").get(this);
    } catch (ReflectiveOperationException | ClassCastException e) {
      throw new IllegalStateException(
          "Can't change the configuration of " + MultithreadedHttpClient.class.getName(), e);
    }
  }

  private static Field getField(String name) throws NoSuchFieldException {
    Field field = MultithreadedHttpClient.class.getDeclaredField(name);
    field.setAccessible(true);
    return field;
  }

  /** Sets the connect timeout in seconds, also for new connections of the connection manager. */
  @Override
  public void setConnectTimeout(int connectTimeout) {
    super.setConnectTimeout(connectTimeout);
    try {
      connectionManager.setDefaultConnectionConfig((ConnectionConfig) connectionConfigField.get(this));
    } catch (IllegalAccessException e) {
      throw new IllegalStateException(e);
    }
  }
}
