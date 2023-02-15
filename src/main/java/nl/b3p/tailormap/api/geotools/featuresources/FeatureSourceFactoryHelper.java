/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.geotools.featuresources;

import java.io.IOException;
import nl.b3p.tailormap.api.persistence.FeatureSource;
import nl.b3p.tailormap.api.persistence.FeatureType;
import org.geotools.data.simple.SimpleFeatureSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class FeatureSourceFactoryHelper {

  @Value("${tailormap-api.timeout}")
  private int timeout;

  public SimpleFeatureSource openGeoToolsFeatureSource(FeatureType ft) throws IOException {
    return FeatureSourceFactoryHelper.openGeoToolsFeatureSource(ft.getFeatureSource(), ft, timeout);
  }

  public static SimpleFeatureSource openGeoToolsFeatureSource(
      FeatureSource fs, FeatureType sft, int timeout) throws IOException {
    FeatureSourceHelper sh = getHelper(fs);
    return sh.openGeoToolsFeatureSource(fs, sft, timeout);
  }

  private static FeatureSourceHelper getHelper(FeatureSource fs) {
    switch (fs.getProtocol()) {
      case JDBC:
        return new JDBCFeatureSourceHelper();
      case WFS:
        return new WFSFeatureSourceHelper();
      default:
        throw new IllegalArgumentException("Invalid protocol: " + fs.getProtocol());
    }
  }
}
