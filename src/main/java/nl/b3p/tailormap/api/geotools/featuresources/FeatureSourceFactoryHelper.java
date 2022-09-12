/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.geotools.featuresources;

import nl.tailormap.viewer.config.services.FeatureSource;
import nl.tailormap.viewer.config.services.JDBCFeatureSource;
import nl.tailormap.viewer.config.services.SimpleFeatureType;
import nl.tailormap.viewer.config.services.WFSFeatureSource;

import org.geotools.data.simple.SimpleFeatureSource;

import java.io.IOException;

public class FeatureSourceFactoryHelper {

    public static SimpleFeatureSource openGeoToolsFeatureSource(SimpleFeatureType sft)
            throws IOException {
        return FeatureSourceFactoryHelper.openGeoToolsFeatureSource(
                sft.getFeatureSource(), sft, 30);
    }

    public static SimpleFeatureSource openGeoToolsFeatureSource(
            FeatureSource fs, SimpleFeatureType sft, int timeout) throws IOException {
        FeatureSourceHelper sh = getHelper(fs);
        assert sh != null;
        return sh.openGeoToolsFeatureSource(fs, sft, timeout);
    }

    private static FeatureSourceHelper getHelper(FeatureSource fs) {
        if (fs instanceof JDBCFeatureSource) {
            return new JDBCFeatureSourceHelper();
        } else if (fs instanceof WFSFeatureSource) {
            return new WFSFeatureSourceHelper();
        }
        // should never happen
        return null;
    }
}
