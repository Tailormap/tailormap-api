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
import org.geotools.http.SimpleHttpClient;

/**
 * GeoTools HTTP client factory that uses {@link NoExpectContinueHttpClient} instead of
 * {@link org.geotools.http.commons.MultithreadedHttpClient}. Registered as a service in
 * {@code META-INF/services/org.geotools.http.HTTPClientFactory} and made the default by setting the
 * {@link org.geotools.util.factory.Hints#HTTP_CLIENT_FACTORY} hint in
 * {@link org.tailormap.api.configuration.GeoToolsConfiguration}.
 *
 * <p>Without the hint, GeoTools creates a {@link SimpleHttpClient} (from
 * {@link org.geotools.http.DefaultHTTPClientFactory}) unless a behavior such as {@link HTTPConnectionPooling} is
 * requested, which the WFS datastore does. Because the hint makes this factory handle all requests, it does the same:
 * it only creates the pooled {@link NoExpectContinueHttpClient} when {@link SimpleHttpClient} doesn't support a
 * requested behavior. {@link SimpleHttpClient} doesn't send {@code Expect: 100-continue}, applies its connect timeout
 * (the pooled client ignores {@code setConnectTimeout()} in GeoTools 35.2) and doesn't need to be closed, so code that
 * uses the default client such as WMS/WMTS capabilities loading doesn't change.
 */
public class NoExpectContinueHttpClientFactory extends AbstractHTTPClientFactory {
  @Override
  public List<Class<?>> clientClasses() {
    return List.of(SimpleHttpClient.class, NoExpectContinueHttpClient.class);
  }

  @Override
  public HTTPClient createClient(List<Class<? extends HTTPBehavior>> behaviors) {
    if (behaviors.stream().allMatch(behavior -> behavior.isAssignableFrom(SimpleHttpClient.class))) {
      return new SimpleHttpClient();
    }
    return new NoExpectContinueHttpClient();
  }

  @Override
  protected HTTPClient createLogging(HTTPClient client) {
    if (client instanceof HTTPConnectionPooling) {
      // Keep HTTPConnectionPooling available when the HTTP_LOGGING hint is set: the WFS datastore casts to it
      return new LoggingConnectionPoolingHTTPClient(client);
    }
    return super.createLogging(client);
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
