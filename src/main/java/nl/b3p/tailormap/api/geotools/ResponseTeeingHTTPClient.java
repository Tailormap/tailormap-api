/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.geotools;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;
import org.geotools.http.HTTPClient;
import org.geotools.http.HTTPResponse;

public class ResponseTeeingHTTPClient implements HTTPClient {

  public static class TeeHTTPResponseWrapper implements HTTPResponse {
    private final HTTPResponse wrapped;
    private final ByteArrayOutputStream copy = new ByteArrayOutputStream();

    private String contentType;

    public TeeHTTPResponseWrapper(HTTPResponse wrapped) {
      this.wrapped = wrapped;
    }

    public byte[] getCopy() {
      return copy.toByteArray();
    }

    // <editor-fold desc="methods directly delegated to wrapped object">
    @Override
    public void dispose() {
      wrapped.dispose();
    }

    @Override
    public String getContentType() {
      if (contentType == null) {
        contentType = wrapped.getContentType();
      }
      return contentType;
    }

    @Override
    public String getResponseHeader(String s) {
      return wrapped.getResponseHeader(s);
    }

    @Override
    public InputStream getResponseStream() throws IOException {
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

  public ResponseTeeingHTTPClient(HTTPClient wrapped) {
    this.wrapped = wrapped;
  }

  @Override
  public HTTPResponse get(URL url, Map<String, String> headers) throws IOException {
    this.responseWrapper = new TeeHTTPResponseWrapper(wrapped.get(url, headers));
    return responseWrapper;
  }

  @Override
  public HTTPResponse post(URL url, InputStream inputStream, String s) throws IOException {
    this.responseWrapper = new TeeHTTPResponseWrapper(wrapped.post(url, inputStream, s));
    return responseWrapper;
  }

  @Override
  public HTTPResponse get(URL url) throws IOException {
    this.responseWrapper = new TeeHTTPResponseWrapper(wrapped.get(url));
    return responseWrapper;
  }

  public HTTPResponse getResponse() {
    return responseWrapper;
  }

  public byte[] getResponseCopy() {
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
