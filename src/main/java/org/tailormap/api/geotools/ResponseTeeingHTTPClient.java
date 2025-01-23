/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.geotools;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import org.geotools.http.HTTPClient;
import org.geotools.http.HTTPResponse;

/**
 * Wrapper for a GeoTools HTTPClient that allows access to the response headers and body after the original response has
 * been consumed and disposed of by other code. The response body for the latest request is kept in memory, a consumer
 * can be specified to get responses of earlier requests. Response headers of an unwrapped HTTPClient can only be
 * retrieved when the response isn't disposed, this wrapper allows response headers to be cached for retrieval even
 * after disposal.
 */
public class ResponseTeeingHTTPClient implements HTTPClient {

  public class TeeHTTPResponseWrapper implements HTTPResponse {
    private final HTTPResponse wrapped;

    private boolean disposed = false;
    private final ByteArrayOutputStream copy = new ByteArrayOutputStream();

    private String contentType;
    private final Map<String, String> cachedResponseHeaders = new HashMap<>();

    public TeeHTTPResponseWrapper(HTTPResponse wrapped) {
      this.wrapped = wrapped;
    }

    public byte[] getCopy() {
      return copy.toByteArray();
    }

    // <editor-fold desc="methods directly delegated to wrapped object">
    @Override
    public void dispose() {
      disposed = true;
      wrapped.dispose();
    }

    public boolean isDisposed() {
      return disposed;
    }

    @Override
    public String getContentType() {
      if (contentType == null) {
        contentType = wrapped.getContentType();
      }
      return contentType;
    }

    @Override
    public String getResponseHeader(String header) {
      if (cachedResponseHeaders.containsKey(header)) {
        return cachedResponseHeaders.get(header);
      }
      // When disposed, the wrapped client will probably throw a NPE when trying to get a response
      // header
      if (isDisposed()) {
        return null;
      }
      return wrapped.getResponseHeader(header);
    }

    @Override
    public InputStream getResponseStream() throws IOException {
      // Cache response headers now, when the internal connection is still available
      for (String header : responseHeadersToCache) {
        cachedResponseHeaders.put(header, wrapped.getResponseHeader(header));
      }
      return new org.apache.commons.io.input.TeeInputStream(wrapped.getResponseStream(), copy);
    }

    @Override
    public String getResponseCharset() {
      return wrapped.getResponseCharset();
    }
    // </editor-fold>
  }

  private TeeHTTPResponseWrapper responseWrapper;

  private final HTTPClient wrapped;

  private final BiConsumer<URL, TeeHTTPResponseWrapper> requestConsumer;

  private final Set<String> responseHeadersToCache;

  /**
   * Wrap a GeoTools HTTPClient allowing access to the latest response body after it has been consumed by handing the
   * client to a GeoTools module. Note that getting the response headers after the original response has been consumed
   * will return null without explicitly calling the other constructor with the responseHeadersToCache parameter.
   *
   * @param wrapped The GeoTools HTTPClient to wrap
   */
  public ResponseTeeingHTTPClient(HTTPClient wrapped) {
    this(wrapped, null, null);
  }

  /**
   * Wrap a GeoTools HTTPClient allowing access to the all responses after they have been consumed by handing the
   * client to a GeoTools module by passing a consumer for each request URL and wrapped response. The
   * responseHeadersToCache parameter allows access to response headers after the original wrapped response has been
   * disposed (only cached when getResponseStream() is requested).
   *
   * @param wrapped The GeoTools HTTPClient to wrap
   * @param requestConsumer Consumer for each request so not only the latest response can be accessed, may be null
   * @param responseHeadersToCache Which response headers to cache, may be null
   */
  public ResponseTeeingHTTPClient(
      HTTPClient wrapped,
      BiConsumer<URL, TeeHTTPResponseWrapper> requestConsumer,
      Set<String> responseHeadersToCache) {
    this.wrapped = wrapped;
    this.requestConsumer = requestConsumer == null ? (url, response) -> {} : requestConsumer;
    this.responseHeadersToCache = responseHeadersToCache == null ? Collections.emptySet() : responseHeadersToCache;
  }

  @Override
  public HTTPResponse get(URL url, Map<String, String> headers) throws IOException {
    this.responseWrapper = new TeeHTTPResponseWrapper(wrapped.get(url, headers));
    requestConsumer.accept(url, this.responseWrapper);
    return responseWrapper;
  }

  @Override
  public HTTPResponse post(URL url, InputStream inputStream, String s) throws IOException {
    this.responseWrapper = new TeeHTTPResponseWrapper(wrapped.post(url, inputStream, s));
    requestConsumer.accept(url, this.responseWrapper);
    return responseWrapper;
  }

  @Override
  public HTTPResponse get(URL url) throws IOException {
    this.responseWrapper = new TeeHTTPResponseWrapper(wrapped.get(url));
    requestConsumer.accept(url, this.responseWrapper);
    return responseWrapper;
  }

  public HTTPResponse getLatestResponse() {
    return responseWrapper;
  }

  public byte[] getLatestResponseCopy() {
    return responseWrapper == null ? null : responseWrapper.getCopy();
  }

  // <editor-fold desc="methods directly delegated to wrapped object">
  @Override
  public String getUser() {
    return wrapped.getUser();
  }

  @Override
  public void setUser(String user) {
    wrapped.setUser(user);
  }

  @Override
  public String getPassword() {
    return wrapped.getPassword();
  }

  @Override
  public void setPassword(String password) {
    wrapped.setPassword(password);
  }

  @Override
  public Map<String, String> getExtraParams() {
    return wrapped.getExtraParams();
  }

  @Override
  public void setExtraParams(Map<String, String> extraParams) {
    wrapped.setExtraParams(extraParams);
  }

  @Override
  public int getConnectTimeout() {
    return wrapped.getConnectTimeout();
  }

  @Override
  public void setConnectTimeout(int connectTimeout) {
    wrapped.setConnectTimeout(connectTimeout);
  }

  @Override
  public int getReadTimeout() {
    return wrapped.getReadTimeout();
  }

  @Override
  public void setReadTimeout(int readTimeout) {
    wrapped.setReadTimeout(readTimeout);
  }

  @Override
  public void setTryGzip(boolean tryGZIP) {
    wrapped.setTryGzip(tryGZIP);
  }

  @Override
  public boolean isTryGzip() {
    return wrapped.isTryGzip();
  }
  // </editor-fold>
}
