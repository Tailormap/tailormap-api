/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.geotools.featuresources;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.geotools.api.data.DataStore;
import org.geotools.api.data.ResourceInfo;
import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.data.wfs.WFSDataStoreFactory;
import org.geotools.data.wfs.internal.FeatureTypeInfo;
import org.tailormap.api.geotools.wfs.SimpleWFSHelper;
import org.tailormap.api.persistence.TMFeatureSource;
import org.tailormap.api.persistence.TMFeatureType;
import org.tailormap.api.persistence.helper.GeoToolsHelper;
import org.tailormap.api.persistence.json.ServiceAuthentication;
import org.tailormap.api.persistence.json.TMFeatureTypeInfo;

public class WFSFeatureSourceHelper extends FeatureSourceHelper {
  @Override
  public DataStore createDataStore(TMFeatureSource tmfs, Integer timeout) throws IOException {
    Map<String, Object> params = new HashMap<>();
    if (timeout != null) {
      params.put(WFSDataStoreFactory.TIMEOUT.key, timeout);
    }

    // This sets the VERSION parameter to the default WFS version
    // (SimpleWFSHelper.DEFAULT_WFS_VERSION), which cannot be overridden by configuring a URL with
    // VERSION=2.0.0 parameter
    params.put(
        WFSDataStoreFactory.URL.key,
        SimpleWFSHelper.getWFSRequestURL(tmfs.getUrl(), "GetCapabilities").toURL());

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

  @Override
  protected TMFeatureTypeInfo getFeatureTypeInfo(
      TMFeatureType pft, ResourceInfo info, SimpleFeatureSource gtFs) {
    TMFeatureTypeInfo tmInfo = super.getFeatureTypeInfo(pft, info, gtFs);
    if (info instanceof FeatureTypeInfo ftInfo) {
      tmInfo
          .schema(info.getSchema())
          .wgs84BoundingBox(GeoToolsHelper.fromEnvelope(ftInfo.getWGS84BoundingBox()))
          .defaultSrs(ftInfo.getDefaultSRS())
          .otherSrs(Set.copyOf(ftInfo.getOtherSRS()))
          .outputFormats(ftInfo.getOutputFormats())
          .abstractText(ftInfo.getAbstract());
    }
    return tmInfo;
  }
}
