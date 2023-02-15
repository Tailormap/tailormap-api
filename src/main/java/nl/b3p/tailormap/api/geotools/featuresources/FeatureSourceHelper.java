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

public interface FeatureSourceHelper {
  SimpleFeatureSource openGeoToolsFeatureSource(FeatureSource fs, FeatureType sft, int timeout)
      throws IOException;
}
