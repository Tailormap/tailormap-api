/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.geotools;

import java.io.IOException;
import org.geotools.util.PreventLocalEntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * An entity resolver that extends the {@link PreventLocalEntityResolver} to allow certain DescribeFeatureType requests
 * commonly produced by GeoServer.
 *
 * @see <a href="https://osgeo-org.atlassian.net/browse/GEOT-7916">GEOT-7916</a>
 */
public class TMPreventLocalEntityResolver extends PreventLocalEntityResolver {
  public static final TMPreventLocalEntityResolver INSTANCE = new TMPreventLocalEntityResolver();

  private static final java.util.regex.Pattern DESCRIBE_FEATURE_TYPE_URL = java.util.regex.Pattern.compile(
      "^https?://[^?#;]*\\?(?:[^#;]*[&;])?request=DescribeFeatureType(?:[&;]|$).*",
      java.util.regex.Pattern.CASE_INSENSITIVE);

  /**
   * Next to what the base class allows, explicitly allow (dynamic) {@code DescribeFeatureType} requests such as
   * {@code https://service.pdok.nl/kadaster/brk-bestuurlijke-gebieden/wfs/v1_0?SERVICE=WFS&VERSION=2.0.0&REQUEST=DescribeFeatureType&TYPENAME=bestuurlijkegebieden:Provinciegebied&OUTPUTFORMAT=application%2Fgml%2Bxml%3B%20version%3D3.2}
   * commonly produced by GeoServer.
   */
  @Override
  public InputSource resolveEntity(String name, String publicId, String baseURI, String systemId)
      throws SAXException, IOException {

    if (systemId != null && DESCRIBE_FEATURE_TYPE_URL.matcher(systemId).matches()) {
      return null;
    }
    return super.resolveEntity(name, publicId, baseURI, systemId);
  }
}
