/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.geotools.featuresources;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import nl.b3p.tailormap.api.persistence.TMFeatureSource;
import nl.b3p.tailormap.api.persistence.json.ServiceAuthentication;
import org.geotools.data.DataStore;
import org.geotools.data.wfs.WFSDataStoreFactory;

public class WFSFeatureSourceHelper extends FeatureSourceHelper {
  @Override
  public DataStore createDataStore(TMFeatureSource tmfs, Integer timeout) throws IOException {
    Map<String, Object> params = new HashMap<>();
    if (timeout != null) {
      params.put(WFSDataStoreFactory.TIMEOUT.key, timeout);
    }

    // Params which can not be overridden below
    String wfsUrl = tmfs.getUrl();
    if (!wfsUrl.endsWith("&") && !wfsUrl.endsWith("?")) {
      wfsUrl += wfsUrl.contains("?") ? "&" : "?";
    }
    wfsUrl = wfsUrl + "REQUEST=GetCapabilities&SERVICE=WFS";
    if (!wfsUrl.toUpperCase().contains("VERSION")) {
      wfsUrl += "&VERSION=1.1.0";
    }

    params.put(WFSDataStoreFactory.URL.key, wfsUrl);

    ServiceAuthentication authentication = tmfs.getAuthentication();
    if (authentication != null) {
      if (authentication.getMethod() != ServiceAuthentication.MethodEnum.PASSWORD) {
        throw new IllegalArgumentException(authentication.getMethod().getValue());
      }
      params.put(WFSDataStoreFactory.USERNAME.key, authentication.getUsername());
      params.put(WFSDataStoreFactory.PASSWORD.key, authentication.getPassword());
    }
    return openDatastore(params, WFSDataStoreFactory.PASSWORD.key);
  }
}
