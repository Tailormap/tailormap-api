/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.geotools.featuresources;

import java.io.IOException;
import org.geotools.api.data.SimpleFeatureSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.tailormap.api.persistence.TMFeatureSource;
import org.tailormap.api.persistence.TMFeatureType;

@Service
public class FeatureSourceFactoryHelper {

  @Value("${tailormap-api.timeout}")
  private int timeout;

  public SimpleFeatureSource openGeoToolsFeatureSource(TMFeatureType tmft) throws IOException {
    return openGeoToolsFeatureSource(tmft, timeout);
  }

  public SimpleFeatureSource openGeoToolsFeatureSource(TMFeatureType tmft, int timeout) throws IOException {
    FeatureSourceHelper sh = getHelper(tmft.getFeatureSource());
    return sh.openGeoToolsFeatureSource(tmft, timeout);
  }

  private FeatureSourceHelper getHelper(TMFeatureSource fs) {
    return switch (fs.getProtocol()) {
      case JDBC -> new JDBCFeatureSourceHelper();
      case WFS -> new WFSFeatureSourceHelper();
      default -> throw new IllegalArgumentException("Invalid protocol: " + fs.getProtocol());
    };
  }
}
