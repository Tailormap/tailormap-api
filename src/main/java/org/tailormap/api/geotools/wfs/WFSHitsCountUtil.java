/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.geotools.wfs;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.geotools.api.data.Query;
import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.filter.Filter;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.data.wfs.InvertAxisFilterVisitor;
import org.geotools.data.wfs.WFSDataStore;
import org.geotools.data.wfs.internal.GetFeatureRequest;
import org.geotools.data.wfs.internal.GetFeatureRequest.ResultType;
import org.geotools.data.wfs.internal.WFSClient;
import org.geotools.data.wfs.internal.WFSConfig;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.http.HTTPClient;
import org.geotools.http.HTTPResponse;
import org.jspecify.annotations.Nullable;
import org.locationtech.jts.geom.GeometryFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Counts features with a WFS {@code resultType=hits} GetFeature request when GeoTools does not.
 *
 * <p>GeoTools only does a hits request for WFS 1.x. For WFS 2.0.0 the {@code StrictWFS_2_0_Strategy} does not support
 * hits (its pull parser can't read the count), so {@link SimpleFeatureSource#getCount(Query)} always returns -1 without
 * asking the server. This class does the hits request itself, using the GeoTools request so the filter is encoded the
 * same way as for the GetFeature request for the features, and reads the {@code numberMatched} attribute.
 *
 * <p>A hits request always counts, also on MapServer which returns {@code numberMatched="unknown"} in a GetFeature
 * response with features (unless {@code wfs_compute_number_matched} is set). Counting can be slow for large layers. See
 * {@link #getCount(SimpleFeatureSource, Query, Duration, Duration)} for not waiting long for a slow count.
 */
public final class WFSHitsCountUtil {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  /**
   * WFS feature types (capabilities URL and type name) for which a hits request timed out, with the time until which
   * no hits request is done. In memory only: a slow count is a property of the server and the size of the layer,
   * which rarely changes, and after a restart it is only one hits request to find out again.
   */
  private static final Map<String, Instant> slowHits = new ConcurrentHashMap<>();

  private WFSHitsCountUtil() {}

  /**
   * Count the features of a feature source. Calls {@link SimpleFeatureSource#getCount(Query)} and if that returns -1
   * for a WFS which GeoTools does not count with a hits request (WFS 2.0.0), does a hits request.
   *
   * @param fs the feature source, may be of any type
   * @param query the query
   * @return the number of features, or -1 if unknown
   * @throws IOException when {@link SimpleFeatureSource#getCount(Query)} fails; a failing hits request returns -1
   */
  public static int getCount(SimpleFeatureSource fs, Query query) throws IOException {
    return countWithHits(fs, query, null, null);
  }

  /**
   * Count the features of a feature source like {@link #getCount(SimpleFeatureSource, Query)}, but without waiting
   * long for a slow hits request. For when the count is nice to have, such as the total for a page of features.
   *
   * <p>A hits request which takes longer than {@code hitsTimeout} is aborted and the feature type is remembered as
   * slow, so it isn't counted with a hits request for {@code slowHitsTtl} and the count is -1 without waiting. Some
   * servers count very slowly for large layers (PDOK NWB wegvakken: ~18 s for a hits request versus 0.1 s for a page
   * of features), while counting is quick for most.
   *
   * @param fs the feature source, may be of any type
   * @param query the query
   * @param hitsTimeout how long to wait for a hits request, in whole seconds (at least 1 s, the HTTP read timeout)
   * @param slowHitsTtl how long not to count a feature type after its hits request timed out
   * @return the number of features, or -1 if unknown or too slow to count
   * @throws IOException when {@link SimpleFeatureSource#getCount(Query)} fails; a failing hits request returns -1
   */
  public static int getCount(SimpleFeatureSource fs, Query query, Duration hitsTimeout, Duration slowHitsTtl)
      throws IOException {
    return countWithHits(fs, query, hitsTimeout, slowHitsTtl);
  }

  /** Without {@code hitsTimeout} the normal read timeout is used and slow feature types are not remembered. */
  @SuppressWarnings("PMD.UseWFSHitsCountUtil")
  private static int countWithHits(
      SimpleFeatureSource fs, Query query, @Nullable Duration hitsTimeout, @Nullable Duration slowHitsTtl)
      throws IOException {
    int count = fs.getCount(query);
    if (count >= 0
        || !(fs.getDataStore() instanceof WFSDataStore wfsDataStore)
        || wfsDataStore.getWfsClient().canCount()) {
      // When the client can count, GeoTools already did a hits request
      return count;
    }
    String slowHitsKey = null;
    if (hitsTimeout != null && slowHitsTtl != null) {
      slowHitsKey = wfsDataStore.getInfo().getSource() + "|" + wfsDataStore.getRemoteTypeName(fs.getName());
      if (isSlowHits(slowHitsKey)) {
        logger.debug("Not counting features of {}, an earlier hits request timed out", slowHitsKey);
        return -1;
      }
    }
    try {
      return getHitsCount(wfsDataStore, fs, query, hitsTimeout);
    } catch (Exception e) {
      if (slowHitsKey != null && ExceptionUtils.indexOfType(e, SocketTimeoutException.class) >= 0) {
        logger.info(
            "WFS hits request to count features of {} took longer than {}, not counting for {}",
            slowHitsKey,
            hitsTimeout,
            slowHitsTtl);
        slowHits.put(slowHitsKey, Instant.now().plus(slowHitsTtl));
        return -1;
      }
      logger.warn("WFS hits request to count features of {} failed: {}", fs.getName(), e.getMessage());
      logger.debug("WFS hits request exception", e);
      return -1;
    }
  }

  private static boolean isSlowHits(String slowHitsKey) {
    Instant until = slowHits.get(slowHitsKey);
    if (until == null) {
      return false;
    }
    if (until.isBefore(Instant.now())) {
      slowHits.remove(slowHitsKey, until);
      return false;
    }
    return true;
  }

  /** Forget the feature types for which a hits request timed out. For tests. */
  static void clearSlowHits() {
    slowHits.clear();
  }

  private static int getHitsCount(
      WFSDataStore wfsDataStore, SimpleFeatureSource fs, Query query, @Nullable Duration hitsTimeout)
      throws IOException, XMLStreamException {
    WFSClient client = wfsDataStore.getWfsClient();
    QName remoteTypeName = wfsDataStore.getRemoteTypeName(fs.getName());
    SimpleFeatureType remoteFeatureType = wfsDataStore.getRemoteSimpleFeatureType(remoteTypeName);

    Filter filter = query.getFilter() == null ? Filter.INCLUDE : query.getFilter();
    Filter[] split = client.splitFilters(remoteTypeName, filter);
    if (!Filter.INCLUDE.equals(split[1])) {
      // Part of the filter must be evaluated after retrieving the features, the server can't count
      logger.debug("Filter not fully supported by WFS, can't count with hits request: {}", filter);
      return -1;
    }
    filter = invertAxisInFilterIfNeeded(client, split[0], query, remoteFeatureType);

    GetFeatureRequest request = client.createGetFeatureRequest();
    request.setTypeName(remoteTypeName);
    request.setFullType(remoteFeatureType);
    request.setFilter(filter);
    request.setResultType(ResultType.HITS);
    request.setHints(query.getHints());

    // The data store and its HTTP client are not shared between threads (see FeatureSourceFactoryHelper), so the
    // read timeout can be changed for this request only
    HTTPClient http = client.getHTTPClient();
    int readTimeout = http.getReadTimeout();
    if (hitsTimeout != null) {
      http.setReadTimeout((int) Math.max(1, hitsTimeout.toSeconds()));
    }
    try {
      HTTPResponse response;
      if (request.requiresPost()) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        request.performPostOutput(out);
        response = http.post(
            request.getFinalURL(),
            new ByteArrayInputStream(out.toByteArray()),
            request.getPostContentType());
      } else {
        response = http.get(request.getFinalURL());
      }
      try (InputStream in = response.getResponseStream()) {
        int count = parseNumberMatched(in);
        logger.debug("WFS hits request for {} returned {} features", remoteTypeName, count);
        return count;
      } finally {
        response.dispose();
      }
    } finally {
      http.setReadTimeout(readTimeout);
    }
  }

  /**
   * Same as the private {@code WFSFeatureSource.invertAxisInFilterIfNeeded()} which GeoTools applies to the filter of
   * a GetFeature request.
   */
  private static Filter invertAxisInFilterIfNeeded(
      WFSClient client, Filter filter, Query query, SimpleFeatureType featureType) {
    CoordinateReferenceSystem crs = query.getCoordinateSystem();
    if (crs == null) {
      crs = featureType.getCoordinateReferenceSystem();
    }
    if (WFSConfig.invertAxisNeeded(client.getAxisOrderFilter(), crs)) {
      return (Filter) filter.accept(
          new InvertAxisFilterVisitor(CommonFactoryFinder.getFilterFactory(null), new GeometryFactory()),
          null);
    }
    return filter;
  }

  /**
   * Read the number of features from a GetFeature hits response: the {@code numberMatched} attribute (WFS 2.0.0) or
   * the {@code numberOfFeatures} attribute (WFS 1.x) of the root {@code FeatureCollection} element.
   *
   * @param in the response
   * @return the number of features, or -1 if the response is not a feature collection (for example an exception
   *     report) or the number is not a non-negative integer (for example {@code "unknown"})
   * @throws XMLStreamException if the response is not XML
   */
  public static int parseNumberMatched(InputStream in) throws XMLStreamException {
    XMLInputFactory factory = XMLInputFactory.newInstance();
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    XMLStreamReader reader = factory.createXMLStreamReader(in);
    try {
      while (reader.hasNext()) {
        if (reader.next() == XMLStreamConstants.START_ELEMENT) {
          if (!"FeatureCollection".equals(reader.getLocalName())) {
            logger.warn("Unexpected root element in WFS hits response: {}", reader.getName());
            return -1;
          }
          String value = reader.getAttributeValue(null, "numberMatched");
          if (value == null) {
            value = reader.getAttributeValue(null, "numberOfFeatures");
          }
          return parseCount(value);
        }
      }
      return -1;
    } finally {
      reader.close();
    }
  }

  private static int parseCount(String value) {
    if (value == null) {
      return -1;
    }
    try {
      int count = Integer.parseInt(value.trim());
      return count >= 0 ? count : -1;
    } catch (NumberFormatException e) {
      // "unknown"
      return -1;
    }
  }
}
