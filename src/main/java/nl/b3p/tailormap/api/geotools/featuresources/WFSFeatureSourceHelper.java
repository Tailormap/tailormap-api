/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.geotools.featuresources;

import nl.tailormap.viewer.config.services.FeatureSource;
import nl.tailormap.viewer.config.services.SimpleFeatureType;
import nl.tailormap.viewer.config.services.WFSFeatureSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.wfs.WFSDataStoreFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class WFSFeatureSourceHelper implements FeatureSourceHelper {
    private static final Log log = LogFactory.getLog(WFSFeatureSourceHelper.class);

    public static DataStore createDataStore(
            Map<String, Object> extraDataStoreParams, WFSFeatureSource fs) throws IOException {
        Map<String, Object> params = new HashMap<>();

        // Params which can be overridden
        params.put(WFSDataStoreFactory.TIMEOUT.key, TIMEOUT);

        if (extraDataStoreParams != null) {
            params.putAll(extraDataStoreParams);
        }

        // Params which can not be overridden below
        String wfsUrl = fs.getUrl();
        if (!wfsUrl.endsWith("&") && !wfsUrl.endsWith("?")) {
            wfsUrl += wfsUrl.contains("?") ? "&" : "?";
        }
        wfsUrl = wfsUrl + "REQUEST=GetCapabilities&SERVICE=WFS";
        if (!wfsUrl.toUpperCase().contains("VERSION")) {
            wfsUrl += "&VERSION=1.1.0";
        }

        params.put(WFSDataStoreFactory.URL.key, wfsUrl);
        params.put(WFSDataStoreFactory.USERNAME.key, fs.getUsername());
        params.put(WFSDataStoreFactory.PASSWORD.key, fs.getPassword());

        Map<String, Object> logParams = new HashMap<>(params);
        if (fs.getPassword() != null) {
            logParams.put(
                    WFSDataStoreFactory.PASSWORD.key,
                    String.valueOf(new char[fs.getPassword().length()]).replace("\0", "*"));
        }
        log.debug("Opening datastore using parameters: " + logParams);
        DataStore ds = DataStoreFinder.getDataStore(params);
        if (ds == null) {
            throw new IOException("Cannot open datastore using parameters " + logParams);
        }
        return ds;
    }

    public static SimpleFeatureSource openGeoToolsFSFeatureSource(
            WFSFeatureSource fs, SimpleFeatureType sft, int timeout) throws IOException {
        Map<String, Object> extraParams = new HashMap<>();
        extraParams.put(WFSDataStoreFactory.TIMEOUT.key, timeout);
        DataStore ds = WFSFeatureSourceHelper.createDataStore(extraParams, fs);

        return ds.getFeatureSource(sft.getTypeName());
    }

    @Override
    public SimpleFeatureSource openGeoToolsFeatureSource(FeatureSource fs, SimpleFeatureType sft)
            throws IOException {
        return WFSFeatureSourceHelper.openGeoToolsFSFeatureSource(
                (WFSFeatureSource) fs, sft, TIMEOUT);
    }

    @Override
    public SimpleFeatureSource openGeoToolsFeatureSource(
            FeatureSource fs, SimpleFeatureType sft, int timeout) throws IOException {
        return WFSFeatureSourceHelper.openGeoToolsFSFeatureSource(
                (WFSFeatureSource) fs, sft, timeout);
    }
}
