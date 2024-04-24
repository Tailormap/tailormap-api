/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.geotools.featuresources;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import nl.b3p.tailormap.api.persistence.TMFeatureSource;
import nl.b3p.tailormap.api.persistence.TMFeatureType;
import nl.b3p.tailormap.api.persistence.helper.GeoToolsHelper;
import nl.b3p.tailormap.api.persistence.json.ServiceAuthentication;
import nl.b3p.tailormap.api.persistence.json.TMFeatureTypeInfo;
import org.geotools.api.data.DataStore;
import org.geotools.api.data.ResourceInfo;
import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.data.wfs.WFSDataStoreFactory;
import org.geotools.data.wfs.internal.FeatureTypeInfo;

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
    if (!wfsUrl.toUpperCase(Locale.ROOT).contains("VERSION")) {
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
