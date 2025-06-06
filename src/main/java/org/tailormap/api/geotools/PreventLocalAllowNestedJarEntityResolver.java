/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.geotools;

import java.io.IOException;
import org.geotools.util.PreventLocalEntityResolver;
import org.xml.sax.SAXException;

/**
 * An entity resolver that allows nested JAR files used in Spring Boot using the jar:nested: protocol to be resolved,
 * specifically for schemas that are packed inside JAR files.
 */
public class PreventLocalAllowNestedJarEntityResolver extends PreventLocalEntityResolver {
  public static final PreventLocalAllowNestedJarEntityResolver INSTANCE =
      new PreventLocalAllowNestedJarEntityResolver();

  @Override
  public org.xml.sax.InputSource resolveEntity(String publicId, String systemId) throws IOException, SAXException {
    if (systemId != null && systemId.matches("(?i)jar:nested:[^?#;]*\\.xsd")) {
      return null;
    }
    return super.resolveEntity(publicId, systemId);
  }
}
