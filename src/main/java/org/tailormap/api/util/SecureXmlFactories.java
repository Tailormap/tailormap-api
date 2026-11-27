/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.util;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLInputFactory;

/**
 * Factories for parsing XML hardened against XXE, for XML from external sources such as responses of OGC services. Use
 * these instead of the JAXP {@code newInstance()} methods, enforced by the PMD rule {@code UseSecureXmlFactories}.
 *
 * <p>Only the JDK XML implementations are on the classpath, so no implementation-specific hardening is needed. A
 * DOCTYPE is allowed because OGC responses can have one (WMS 1.1.1 exception reports usually do), but the external DTD
 * and external entities are never loaded.
 */
public final class SecureXmlFactories {
  private SecureXmlFactories() {}

  /**
   * Create a {@link DocumentBuilderFactory} which does not load external DTDs, entities or schemas.
   *
   * @return the hardened factory
   * @throws ParserConfigurationException if the implementation does not support a feature
   */
  @SuppressWarnings("PMD.UseSecureXmlFactories")
  public static DocumentBuilderFactory newDocumentBuilderFactory() throws ParserConfigurationException {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    factory.setXIncludeAware(false);
    factory.setExpandEntityReferences(false);
    return factory;
  }

  /**
   * Create an {@link XMLInputFactory} (StAX) which does not process DTDs or external entities.
   *
   * @return the hardened factory
   */
  @SuppressWarnings("PMD.UseSecureXmlFactories")
  public static XMLInputFactory newXMLInputFactory() {
    XMLInputFactory factory = XMLInputFactory.newInstance();
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    return factory;
  }
}
