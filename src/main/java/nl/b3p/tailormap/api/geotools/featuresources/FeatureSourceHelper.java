/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.geotools.featuresources;

import java.io.IOException;
import nl.tailormap.viewer.config.services.FeatureSource;
import nl.tailormap.viewer.config.services.SimpleFeatureType;
import org.geotools.data.simple.SimpleFeatureSource;

public interface FeatureSourceHelper {

  Integer TIMEOUT = 60000;

  default SimpleFeatureSource openGeoToolsFeatureSource(SimpleFeatureType sft) throws IOException {
    return openGeoToolsFeatureSource(sft.getFeatureSource(), sft, TIMEOUT);
  }

  default SimpleFeatureSource openGeoToolsFeatureSource(FeatureSource fs, SimpleFeatureType sft)
      throws IOException {
    return openGeoToolsFeatureSource(fs, sft, TIMEOUT);
  }

  SimpleFeatureSource openGeoToolsFeatureSource(
      FeatureSource fs, SimpleFeatureType sft, int timeout) throws IOException;
}
