/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.geotools;

import java.io.ByteArrayInputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class WMSServiceExceptionUtil {

  /**
   * Create a {@link DocumentBuilderFactory} hardened against XXE for parsing a response from a (possibly malicious)
   * WMS. A DOCTYPE is allowed because WMS 1.1.1 exception reports usually have one, but the external DTD and external
   * entities are never loaded. Only the JDK parser is on the classpath, so no implementation-specific hardening is
   * needed.
   */
  private static DocumentBuilderFactory newDocumentBuilderFactory() throws ParserConfigurationException {
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
   * Tries to parse a WMS service exception XML response of any WMS version and extract an error message.
   *
   * @param body The response body from a text/xml WMS response
   * @return A service exception message or null if it can't be extracted
   */
  public static String tryGetServiceExceptionMessage(byte[] body) {
    try {
      DocumentBuilder builder = newDocumentBuilderFactory().newDocumentBuilder();
      Document doc = builder.parse(new ByteArrayInputStream(body));

      String code = null;
      String locator = null;
      String text = null;

      if ("ServiceExceptionReport".equals(doc.getDocumentElement().getNodeName())) {
        // WMS 1.3.0

        NodeList exceptions = doc.getDocumentElement().getElementsByTagName("ServiceException");
        if (exceptions.getLength() > 0) {
          Element exception = (Element) exceptions.item(0);
          code = exception.getAttribute("code");
          locator = exception.getAttribute("locator");
          text = exception.getTextContent().trim();
        }
      } else if (doc.getDocumentElement().getNodeName().contains(":ExceptionReport")) {
        // WMS 1.0.0 and 1.1.0

        NodeList children = doc.getDocumentElement().getChildNodes();
        Element exception = null;
        for (int i = 0; i < children.getLength(); i++) {
          if (children.item(i).getNodeType() == Node.ELEMENT_NODE) {
            exception = (Element) children.item(i);
            break;
          }
        }
        if (exception != null) {
          code = exception.getAttribute("exceptionCode");
          locator = exception.getAttribute("locator");
          children = exception.getChildNodes();
          for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() == Node.ELEMENT_NODE) {
              text = children.item(i).getTextContent().trim();
              break;
            }
          }
        }
      }

      if (text != null) {
        if (StringUtils.isNotBlank(locator)) {
          text = "locator: " + locator + ": " + text;
        }
        if (StringUtils.isNotBlank(code)) {
          text = "code: " + code + ": " + text;
        }
        return text;
      } else {
        return null;
      }
    } catch (Exception e) {
      return null;
    }
  }
}
