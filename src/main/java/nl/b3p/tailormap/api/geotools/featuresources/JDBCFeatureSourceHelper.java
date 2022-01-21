/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.geotools.featuresources;

import nl.tailormap.viewer.config.services.FeatureSource;
import nl.tailormap.viewer.config.services.JDBCFeatureSource;
import nl.tailormap.viewer.config.services.SimpleFeatureType;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.sqlserver.SQLServerDataStoreFactory;
import org.geotools.jdbc.JDBCDataStoreFactory;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class JDBCFeatureSourceHelper implements FeatureSourceHelper {

    private static final Log log = LogFactory.getLog(JDBCFeatureSourceHelper.class);

    public static SimpleFeatureSource openGeoToolsFSFeatureSource(
            JDBCFeatureSource fs, SimpleFeatureType sft) throws IOException {
        DataStore ds = createDataStore(fs);
        return ds.getFeatureSource(sft.getTypeName());
    }

    public static DataStore createDataStore(JDBCFeatureSource fs) throws IOException {
        Map<String, Object> params = new HashMap<>();
        JSONObject urlObj = new JSONObject(fs.getUrl());
        params.put("dbtype", urlObj.get("dbtype"));
        params.put("host", urlObj.get("host"));
        params.put("port", urlObj.get("port"));
        params.put("database", urlObj.get("database"));

        params.put("schema", fs.schema);
        params.put("user", fs.getUsername());
        params.put(JDBCDataStoreFactory.FETCHSIZE.key, 50);
        params.put("passwd", fs.getPassword());
        params.put(JDBCDataStoreFactory.EXPOSE_PK.key, true);
        params.put(JDBCDataStoreFactory.PK_METADATA_TABLE.key, "gt_pk_metadata");
        // this key is available in ao. Oracle and MS SQL datastore factories, but not in the common
        // parent..
        // we need this for mssql to determine a featuretype on an empty table
        if (!urlObj.get("dbtype").equals("oracle")) {
            params.put(SQLServerDataStoreFactory.GEOMETRY_METADATA_TABLE.key, "geometry_columns");
        }
        Map<String, Object> logParams = new HashMap<>(params);
        if (fs.getPassword() != null) {
            logParams.put(
                    "passwd",
                    String.valueOf(new char[fs.getPassword().length()]).replace("\0", "*"));
        }
        log.debug("Opening datastore using parameters: " + logParams);
        DataStore ds = DataStoreFinder.getDataStore(params);

        if (ds == null) {
            throw new IOException("Cannot open datastore using parameters " + logParams);
        }
        return ds;
    }

    @Override
    public SimpleFeatureSource openGeoToolsFeatureSource(FeatureSource fs, SimpleFeatureType sft)
            throws IOException {
        return JDBCFeatureSourceHelper.openGeoToolsFSFeatureSource((JDBCFeatureSource) fs, sft);
    }

    @Override
    public SimpleFeatureSource openGeoToolsFeatureSource(
            FeatureSource fs, SimpleFeatureType sft, int timeout) throws IOException {
        return JDBCFeatureSourceHelper.openGeoToolsFSFeatureSource((JDBCFeatureSource) fs, sft);
    }
}
