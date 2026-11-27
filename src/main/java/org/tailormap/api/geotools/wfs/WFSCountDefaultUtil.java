/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.geotools.wfs;

import org.geotools.api.data.DataAccess;
import org.geotools.data.wfs.WFSDataStore;
import org.geotools.data.wfs.internal.WFSGetCapabilities;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Reads the maximum number of features a WFS 2.0.0 server returns for a GetFeature request, advertised in the
 * capabilities as the {@code CountDefault} constraint (for example the GeoServer maxFeatures or MapServer
 * wfs_maxfeatures setting). A server returns at most this number of features, even if a higher count is requested. WFS
 * 1.x servers don't advertise their limit.
 */
public final class WFSCountDefaultUtil {
  private static final String COUNT_DEFAULT = "CountDefault";

  private WFSCountDefaultUtil() {}

  /**
   * Get the {@code CountDefault} of a WFS from the capabilities GeoTools already retrieved when opening the data
   * store, so no extra request is made.
   *
   * @param dataStore the data store, may be of any type
   * @return the CountDefault, or {@code null} if the data store is not a WFS or the WFS has no (valid) CountDefault
   */
  public static Integer getCountDefault(DataAccess<?, ?> dataStore) {
    if (!(dataStore instanceof WFSDataStore wfsDataStore)) {
      return null;
    }
    WFSGetCapabilities capabilities = wfsDataStore.getWfsClient().getCapabilities();
    return capabilities == null ? null : getCountDefault(capabilities.getRawDocument());
  }

  /**
   * Get the {@code CountDefault} from a WFS capabilities document. A constraint on the GetFeature operation takes
   * precedence over one for all operations.
   *
   * @param capabilities the capabilities document
   * @return the CountDefault, or {@code null} if absent or not a positive integer
   */
  public static Integer getCountDefault(Document capabilities) {
    if (capabilities == null) {
      return null;
    }
    Integer operationsMetadataCountDefault = null;
    NodeList elements = capabilities.getElementsByTagName("*");
    for (int i = 0; i < elements.getLength(); i++) {
      Element constraint = (Element) elements.item(i);
      if (!"Constraint".equals(localName(constraint)) || !COUNT_DEFAULT.equals(constraint.getAttribute("name"))) {
        continue;
      }
      Node parent = constraint.getParentNode();
      if (parent instanceof Element parentElement
          && "Operation".equals(localName(parentElement))
          && "GetFeature".equals(parentElement.getAttribute("name"))) {
        Integer value = getDefaultValue(constraint);
        if (value != null) {
          return value;
        }
      } else if (parent instanceof Element parentElement
          && "OperationsMetadata".equals(localName(parentElement))) {
        operationsMetadataCountDefault = getDefaultValue(constraint);
      }
    }
    return operationsMetadataCountDefault;
  }

  private static Integer getDefaultValue(Element constraint) {
    for (Node child = constraint.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof Element element && "DefaultValue".equals(localName(element))) {
        try {
          int value = Integer.parseInt(element.getTextContent().trim());
          return value > 0 ? value : null;
        } catch (NumberFormatException e) {
          return null;
        }
      }
    }
    return null;
  }

  /** The local name of an element, also when the document was parsed without namespace awareness. */
  private static String localName(Element element) {
    if (element.getLocalName() != null) {
      return element.getLocalName();
    }
    String name = element.getNodeName();
    return name.substring(name.indexOf(':') + 1);
  }
}
