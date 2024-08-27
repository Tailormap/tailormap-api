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
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class WMSServiceExceptionUtil {

  /**
   * Tries to parse a WMS service exception XML response of any WMS version and extract an error
   * message.
   *
   * @param body The response body from a text/xml WMS response
   * @return A service exception message or null if it can't be extracted
   */
  public static String tryGetServiceExceptionMessage(byte[] body) {
    try {
      DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
      documentBuilderFactory.setNamespaceAware(true);
      documentBuilderFactory.setExpandEntityReferences(false);
      documentBuilderFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      documentBuilderFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
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
