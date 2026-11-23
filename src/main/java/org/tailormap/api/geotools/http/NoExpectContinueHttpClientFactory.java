/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.geotools.http;

import java.io.IOException;
import java.util.List;
import org.geotools.http.AbstractHTTPClientFactory;
import org.geotools.http.HTTPBehavior;
import org.geotools.http.HTTPClient;
import org.geotools.http.HTTPConnectionPooling;
import org.geotools.http.LoggingHTTPClient;

/**
 * GeoTools HTTP client factory for {@link NoExpectContinueHttpClient}. Registered as a service in
 * {@code META-INF/services/org.geotools.http.HTTPClientFactory} and made the default by setting the
 * {@link org.geotools.util.factory.Hints#HTTP_CLIENT_FACTORY} hint in
 * {@link org.tailormap.api.configuration.GeoToolsConfiguration}.
 */
public class NoExpectContinueHttpClientFactory extends AbstractHTTPClientFactory {
  @Override
  public List<Class<?>> clientClasses() {
    return List.of(NoExpectContinueHttpClient.class);
  }

  @Override
  public HTTPClient createClient(List<Class<? extends HTTPBehavior>> behaviors) {
    return new NoExpectContinueHttpClient();
  }

  @Override
  protected HTTPClient createLogging(HTTPClient client) {
    // Keep HTTPConnectionPooling available when the HTTP_LOGGING hint is set: the WFS datastore casts to it
    return new LoggingConnectionPoolingHTTPClient(client);
  }

  static class LoggingConnectionPoolingHTTPClient extends LoggingHTTPClient implements HTTPConnectionPooling {
    LoggingConnectionPoolingHTTPClient(HTTPClient delegate) {
      super(delegate);
    }

    @Override
    public int getMaxConnections() {
      return ((HTTPConnectionPooling) delegate).getMaxConnections();
    }

    @Override
    public void setMaxConnections(int maxConnections) {
      ((HTTPConnectionPooling) delegate).setMaxConnections(maxConnections);
    }

    @Override
    public void close() throws IOException {
      ((HTTPConnectionPooling) delegate).close();
    }
  }
}
