/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.geotools;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

class TMPreventLocalEntityResolverTest {
  @Test
  void should_allow_http_describeFeatureType_requests() throws Exception {
    String systemId =
        "https://service.example.org/wfs?SERVICE=WFS&VERSION=2.0.0&REQUEST=DescribeFeatureType&TYPENAME=ns:Type";
    InputSource result = TMPreventLocalEntityResolver.INSTANCE.resolveEntity(
        "name", "publicId", "https://service.example.org/wfs", systemId);
    assertNull(result, "DescribeFeatureType HTTP requests should be allowed (resolver returns null)");
  }

  @Test
  void should_allow_describeFeatureType_request_case_insensitive_and_with_other_query_params() throws Exception {
    String systemId = "http://host/service?foo=bar&request=DescribeFeatureTYPE&other=1";
    InputSource result =
        TMPreventLocalEntityResolver.INSTANCE.resolveEntity(null, null, "http://host/service", systemId);
    assertNull(result);
  }

  @Test
  void should_allow_absolute_http_xsd_uris() throws Exception {
    String systemId = "http://schemas.example.org/some/schema.xsd";
    InputSource result = TMPreventLocalEntityResolver.INSTANCE.resolveEntity(null, null, null, systemId);
    assertNull(result, "Absolute http(s) .xsd URIs must be allowed by the resolver");
  }

  @Test
  void should_allow_relative_xsd_resolved_against_baseUri() throws Exception {
    String baseURI = "http://schemas.example.org/path/";
    String systemId = "schema.xsd";
    InputSource result = TMPreventLocalEntityResolver.INSTANCE.resolveEntity(null, null, baseURI, systemId);
    assertNull(result, "Relative .xsd should be resolved against baseURI and allowed");
  }

  @Test
  void should_reject_file_scheme_systemId() {
    String systemId = "file:///etc/passwd";
    assertThrows(
        SAXException.class,
        () -> TMPreventLocalEntityResolver.INSTANCE.resolveEntity(null, null, null, systemId));
  }

  @Test
  void should_allow_absolute_https_scheme_systemId() throws Exception {
    String systemId = "https://example.org/schema.xsd";
    InputSource result = TMPreventLocalEntityResolver.INSTANCE.resolveEntity(null, null, null, systemId);
    assertNull(result, "Absolute https .xsd URIs must be allowed by the resolver");
  }

  @Test
  void should_throw_when_systemId_is_null() {
    assertThrows(
        SAXException.class, () -> TMPreventLocalEntityResolver.INSTANCE.resolveEntity(null, null, null, null));
  }

  @Test
  void should_throw_when_protocol_is_not_supported() {
    String systemId =
        "file://service.example.org/wfs?SERVICE=WFS&VERSION=2.0.0&REQUEST=DescribeFeatureType&TYPENAME=ns:Type";
    assertThrows(
        SAXException.class,
        () -> TMPreventLocalEntityResolver.INSTANCE.resolveEntity(null, null, null, systemId));
  }
}
