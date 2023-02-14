/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.geotools.featuresources;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import nl.b3p.tailormap.api.persistence.FeatureSource;
import nl.b3p.tailormap.api.persistence.FeatureType;
import nl.b3p.tailormap.api.persistence.json.ServiceCaps;
import nl.b3p.tailormap.api.persistence.json.ServiceInfo;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.wfs.WFSDataStoreFactory;

public class WFSFeatureSourceHelper implements FeatureSourceHelper {
  private static final Log log = LogFactory.getLog(WFSFeatureSourceHelper.class);

  public static DataStore createDataStore(
      Map<String, Object> extraDataStoreParams, FeatureSource fs) throws IOException {
    Map<String, Object> params = new HashMap<>();

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
    // params.put(WFSDataStoreFactory.USERNAME.key, fs.getUsername());
    // params.put(WFSDataStoreFactory.PASSWORD.key, fs.getPassword());

    Map<String, Object> logParams = new HashMap<>(params);
    //    if (fs.getPassword() != null) {
    //      logParams.put(
    //          WFSDataStoreFactory.PASSWORD.key,
    //          String.valueOf(new char[fs.getPassword().length()]).replace("\0", "*"));
    //    }
    log.debug("Opening datastore using parameters: " + logParams);
    DataStore ds = DataStoreFinder.getDataStore(params);
    if (ds == null) {
      throw new IOException("Cannot open datastore using parameters " + logParams);
    }
    return ds;
  }

  @Override
  public SimpleFeatureSource openGeoToolsFeatureSource(
      FeatureSource fs, FeatureType sft, int timeout) throws IOException {
    Map<String, Object> extraParams = new HashMap<>();
    extraParams.put(WFSDataStoreFactory.TIMEOUT.key, timeout);
    DataStore ds = WFSFeatureSourceHelper.createDataStore(extraParams, fs);

    return ds.getFeatureSource(sft.getTypeName());
  }

  public void loadCapabilities(FeatureSource pfs, int timeout) throws IOException {
    Map<String, Object> extraParams = new HashMap<>();
    extraParams.put(WFSDataStoreFactory.TIMEOUT.key, timeout);
    DataStore ds = WFSFeatureSourceHelper.createDataStore(extraParams, pfs);

    pfs.setTitle(ds.getInfo().getTitle());

    org.geotools.data.ServiceInfo si = ds.getInfo();
    pfs.setServiceCapabilities(
        new ServiceCaps()
            .serviceInfo(
                new ServiceInfo()
                    .title(si.getTitle())
                    .keywords(si.getKeywords())
                    .description(si.getDescription())
                    .publisher(si.getPublisher())
                    .schema(si.getSchema())
                    .source(si.getSource())));

    String[] typeNames = ds.getTypeNames();
    log.info(String.format("type names for WFS %s: %s", pfs.getUrl(), Arrays.toString(typeNames)));

    for (String typeName : typeNames) {
      pfs.getFeatureTypes().add(new FeatureType().setFeatureSource(pfs).setTypeName(typeName));
    }
  }
}
