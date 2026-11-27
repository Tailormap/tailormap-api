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
import org.geotools.util.PreventLocalEntityResolver;
import org.springframework.util.LinkedCaseInsensitiveMap;
import org.springframework.web.util.UriComponentsBuilder;
import org.tailormap.api.geotools.wfs.SimpleWFSHelper;
import org.tailormap.api.persistence.TMFeatureSource;
import org.tailormap.api.persistence.TMFeatureType;
import org.tailormap.api.persistence.helper.GeoToolsHelper;
import org.tailormap.api.persistence.json.ServiceAuthentication;
import org.tailormap.api.persistence.json.TMFeatureTypeInfo;

public class WFSFeatureSourceHelper extends FeatureSourceHelper {
  /** WFS versions that can be forced with a VERSION parameter in the feature source URL. */
  private static final Set<String> SUPPORTED_WFS_VERSIONS = Set.of("1.0.0", "1.1.0", "2.0.0");

  /**
   * Get the WFS version to use for a feature source URL. This is {@link SimpleWFSHelper#DEFAULT_WFS_VERSION}, unless
   * the URL has a VERSION parameter with a version GeoTools supports, for example to force WFS 1.1.0 for a server
   * with a broken WFS 2.0.0 implementation.
   *
   * @param url the feature source URL
   * @return the WFS version
   */
  public static String getWFSVersion(String url) {
    LinkedCaseInsensitiveMap<String> wfsUrlParams = new LinkedCaseInsensitiveMap<>();
    wfsUrlParams.putAll(
        UriComponentsBuilder.fromUriString(url).build().getQueryParams().toSingleValueMap());
    String version = wfsUrlParams.get("VERSION");
    // Set.of() throws a NullPointerException for contains(null)
    return version != null && SUPPORTED_WFS_VERSIONS.contains(version)
        ? version
        : SimpleWFSHelper.DEFAULT_WFS_VERSION;
  }

  @Override
  public DataStore createDataStore(TMFeatureSource tmfs, Integer timeout) throws IOException {
    Map<String, Object> params = new HashMap<>();
    params.put(WFSDataStoreFactory.ENTITY_RESOLVER.key, PreventLocalEntityResolver.INSTANCE);

    if (timeout != null) {
      params.put(WFSDataStoreFactory.TIMEOUT.key, timeout);
    }

    params.put(
        WFSDataStoreFactory.URL.key,
        SimpleWFSHelper.getWFSRequestURL(tmfs.getUrl(), "GetCapabilities", getWFSVersion(tmfs.getUrl()), null)
            .toURL());

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
  protected TMFeatureTypeInfo getFeatureTypeInfo(TMFeatureType pft, ResourceInfo info, SimpleFeatureSource gtFs) {
    TMFeatureTypeInfo tmInfo = super.getFeatureTypeInfo(pft, info, gtFs);
    if (info instanceof FeatureTypeInfo ftInfo) {
      tmInfo.schema(info.getSchema())
          .wgs84BoundingBox(GeoToolsHelper.fromEnvelope(ftInfo.getWGS84BoundingBox()))
          .defaultSrs(ftInfo.getDefaultSRS())
          .otherSrs(Set.copyOf(ftInfo.getOtherSRS()))
          .outputFormats(ftInfo.getOutputFormats())
          .abstractText(ftInfo.getAbstract());
    }
    return tmInfo;
  }
}
