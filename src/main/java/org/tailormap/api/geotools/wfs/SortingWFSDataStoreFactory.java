/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.geotools.wfs;

import java.io.IOException;
import java.net.URL;
import java.util.Map;
import org.geotools.data.wfs.WFSDataStore;
import org.geotools.data.wfs.WFSDataStoreFactory;
import org.geotools.data.wfs.internal.WFSConfig;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.type.FeatureTypeFactoryImpl;
import org.geotools.http.HTTPClient;
import org.geotools.ows.ServiceException;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.impl.PackedCoordinateSequenceFactory;

/**
 * {@link WFSDataStoreFactory} that creates a {@link WFSDataStore} with a {@link SortingWFSClient}. This is a copy of
 * {@link WFSDataStoreFactory#createDataStore(Map)} (GeoTools 35.2) with only the client class changed, because the
 * client is created inline there. The HTTP client is wrapped in a {@link RecentResponsesHTTPClient} which keeps the
 * start of recent responses, for {@link WFSHitsCountUtil} to read {@code numberMatched} from a GetFeature response.
 *
 * <p>This is not registered as a {@code DataStoreFactorySpi}, use it directly. It can be removed when GeoTools supports
 * sorting for WFS 2.0.0 itself (but the response teeing is still needed to read {@code numberMatched}).
 */
public class SortingWFSDataStoreFactory extends WFSDataStoreFactory {
  @Override
  public WFSDataStore createDataStore(final Map<String, ?> params) throws IOException {
    final WFSConfig config = WFSConfig.fromParams(params);

    String user = config.getUser();
    String password = config.getPassword();
    if ((user == null) != (password == null)) {
      throw new IOException("Cannot define only one of USERNAME or PASSWORD, must define both or neither");
    }

    final URL capabilitiesURL = URL.lookUp(params);

    // Keep the start of the responses to read numberMatched from a GetFeature response, see WFSHitsCountUtil
    final HTTPClient http =
        new RecentResponsesHTTPClient(getHttpClient(params), WFSHitsCountUtil.RESPONSE_HEAD_SIZE);
    http.setTryGzip(config.isTryGZIP());
    http.setUser(user);
    http.setPassword(password);
    int timeoutMillis = config.getTimeoutMillis();
    http.setConnectTimeout(timeoutMillis / 1000);
    http.setReadTimeout(timeoutMillis / 1000);

    SortingWFSClient wfsClient;
    try {
      wfsClient = new SortingWFSClient(capabilitiesURL, http, config);
    } catch (ServiceException e) {
      throw new IOException(e);
    }

    WFSDataStore dataStore = new WFSDataStore(wfsClient);
    dataStore.setFilterFactory(CommonFactoryFinder.getFilterFactory(null));
    dataStore.setGeometryFactory(new GeometryFactory(PackedCoordinateSequenceFactory.DOUBLE_FACTORY));
    dataStore.setFeatureTypeFactory(new FeatureTypeFactoryImpl());
    dataStore.setFeatureFactory(CommonFactoryFinder.getFeatureFactory(null));
    dataStore.setDataStoreFactory(this);
    dataStore.setNamespaceURI(config.getNamespaceOverride());
    return dataStore;
  }
}
