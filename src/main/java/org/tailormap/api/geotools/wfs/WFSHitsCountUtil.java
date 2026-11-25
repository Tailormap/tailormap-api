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
import java.util.List;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
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
import org.locationtech.jts.geom.GeometryFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tailormap.api.geotools.ResponseTeeingHTTPClient;

/**
 * Counts features with a WFS {@code resultType=hits} GetFeature request when GeoTools does not.
 *
 * <p>GeoTools only does a hits request for WFS 1.x. For WFS 2.0.0 the {@code StrictWFS_2_0_Strategy} does not support
 * hits (its pull parser can't read the count), so {@link SimpleFeatureSource#getCount(Query)} always returns -1 without
 * asking the server. This class does the hits request itself, using the GeoTools request so the filter is encoded the
 * same way as for the GetFeature request for the features, and reads the {@code numberMatched} attribute.
 *
 * <p>A hits request always counts, also on MapServer which returns {@code numberMatched="unknown"} in a GetFeature
 * response with features (unless {@code wfs_compute_number_matched} is set). Counting can be slow for large layers.
 *
 * <p>GeoServer does return {@code numberMatched} in a GetFeature response with features. When the features of a page
 * are retrieved anyway, use {@link #capturePageResponses(SimpleFeatureSource)} before and
 * {@link #getCount(SimpleFeatureSource, Query, PageResponseCapture)} after retrieving the page to use that number and
 * skip the hits request.
 */
public final class WFSHitsCountUtil {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  /**
   * How much of the start of each WFS response {@link SortingWFSDataStoreFactory} keeps, enough for the root element
   * with its attributes and namespace declarations.
   */
  public static final int RESPONSE_HEAD_SIZE = 16 * 1024;

  private WFSHitsCountUtil() {}

  /** Marks the most recent WFS response before retrieving a page, see {@link #capturePageResponses}. */
  public static final class PageResponseCapture {
    private final RecentResponsesHTTPClient client;
    private final List<ResponseTeeingHTTPClient.TeeHTTPResponseWrapper> responsesBeforePage;

    private PageResponseCapture(RecentResponsesHTTPClient client) {
      this.client = client;
      this.responsesBeforePage = client.getRecentResponses();
    }

    /**
     * Returns the {@code numberMatched} of the most recent feature collection received after the capture was made.
     *
     * @return the number, or -1 if no feature collection was received or the number is unknown
     */
    @SuppressWarnings("ReferenceEquality")
    int getNumberMatched() {
      List<ResponseTeeingHTTPClient.TeeHTTPResponseWrapper> responses = client.getRecentResponses();
      for (int i = responses.size() - 1; i >= 0; i--) {
        ResponseTeeingHTTPClient.TeeHTTPResponseWrapper response = responses.get(i);
        // Identity: a response object received before the capture
        if (responsesBeforePage.stream().anyMatch(before -> before == response)) {
          break;
        }
        try {
          Integer count = parseCountAttribute(new ByteArrayInputStream(response.getCopy()), false);
          if (count != null) {
            return count;
          }
        } catch (XMLStreamException e) {
          logger.debug("Error parsing WFS response: {}", e.getMessage());
        }
      }
      return -1;
    }
  }

  /**
   * Mark the most recent response of a WFS which GeoTools can't count (WFS 2.0.0), to read {@code numberMatched} from
   * the response with the features of a page which is retrieved after this call. Only works for a data store created
   * by {@link SortingWFSDataStoreFactory}, which keeps the start of the responses.
   *
   * @param fs the feature source, may be of any type
   * @return the capture to pass to {@link #getCount(SimpleFeatureSource, Query, PageResponseCapture)}, or
   *     {@code null} if not applicable
   */
  public static PageResponseCapture capturePageResponses(SimpleFeatureSource fs) {
    if (fs.getDataStore() instanceof WFSDataStore wfsDataStore
        && !wfsDataStore.getWfsClient().canCount()
        && wfsDataStore.getWfsClient().getHTTPClient() instanceof RecentResponsesHTTPClient client) {
      return new PageResponseCapture(client);
    }
    return null;
  }

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
    return getCount(fs, query, null);
  }

  /**
   * Same as {@link #getCount(SimpleFeatureSource, Query)}, but first tries the {@code numberMatched} of a GetFeature
   * response with the features of a page, which is only a number for some servers (GeoServer).
   *
   * @param fs the feature source, may be of any type
   * @param query the query, with the same filter as the query for the page
   * @param pageResponseCapture the capture from {@link #capturePageResponses(SimpleFeatureSource)}, made before
   *     retrieving the page; call this method directly after retrieving the page. May be {@code null}
   * @return the number of features, or -1 if unknown
   * @throws IOException when {@link SimpleFeatureSource#getCount(Query)} fails; a failing hits request returns -1
   */
  @SuppressWarnings("PMD.UseWFSHitsCountUtil")
  public static int getCount(SimpleFeatureSource fs, Query query, PageResponseCapture pageResponseCapture)
      throws IOException {
    // Read the response of the page first, getCount() may do a DescribeFeatureType request
    int pageNumberMatched = pageResponseCapture == null ? -1 : pageResponseCapture.getNumberMatched();
    int count = fs.getCount(query);
    if (count >= 0
        || !(fs.getDataStore() instanceof WFSDataStore wfsDataStore)
        || wfsDataStore.getWfsClient().canCount()) {
      // When the client can count, GeoTools already did a hits request
      return count;
    }
    try {
      QName remoteTypeName = wfsDataStore.getRemoteTypeName(fs.getName());
      Filter filter = query.getFilter() == null ? Filter.INCLUDE : query.getFilter();
      Filter[] split = wfsDataStore.getWfsClient().splitFilters(remoteTypeName, filter);
      if (!Filter.INCLUDE.equals(split[1])) {
        // Part of the filter must be evaluated after retrieving the features, the server can't count
        logger.debug("Filter not fully supported by WFS, can't count: {}", filter);
        return -1;
      }
      if (pageNumberMatched >= 0) {
        logger.debug(
            "WFS response with features for {} has numberMatched {}", remoteTypeName, pageNumberMatched);
        return pageNumberMatched;
      }
      return getHitsCount(wfsDataStore, remoteTypeName, split[0], query);
    } catch (Exception e) {
      logger.warn("WFS hits request to count features of {} failed: {}", fs.getName(), e.getMessage());
      logger.debug("WFS hits request exception", e);
      return -1;
    }
  }

  private static int getHitsCount(WFSDataStore wfsDataStore, QName remoteTypeName, Filter filter, Query query)
      throws IOException, XMLStreamException {
    WFSClient client = wfsDataStore.getWfsClient();
    SimpleFeatureType remoteFeatureType = wfsDataStore.getRemoteSimpleFeatureType(remoteTypeName);
    filter = invertAxisInFilterIfNeeded(client, filter, query, remoteFeatureType);

    GetFeatureRequest request = client.createGetFeatureRequest();
    request.setTypeName(remoteTypeName);
    request.setFullType(remoteFeatureType);
    request.setFilter(filter);
    request.setResultType(ResultType.HITS);
    request.setHints(query.getHints());

    HTTPClient http = client.getHTTPClient();
    HTTPResponse response;
    if (request.requiresPost()) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      request.performPostOutput(out);
      response = http.post(
          request.getFinalURL(), new ByteArrayInputStream(out.toByteArray()), request.getPostContentType());
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
    Integer count = parseCountAttribute(in, true);
    if (count == null) {
      logger.warn("Unexpected root element in WFS hits response");
      return -1;
    }
    return count;
  }

  /**
   * @param in the response, may be only the start of it
   * @param numberOfFeatures whether to fall back to the WFS 1.x {@code numberOfFeatures} attribute; only for a hits
   *     response, in a response with features it is the number of features in the response, not the total
   * @return {@code null} if the response is not a feature collection, -1 if the number is unknown
   */
  private static Integer parseCountAttribute(InputStream in, boolean numberOfFeatures) throws XMLStreamException {
    XMLInputFactory factory = XMLInputFactory.newInstance();
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    XMLStreamReader reader = factory.createXMLStreamReader(in);
    try {
      while (reader.hasNext()) {
        if (reader.next() == XMLStreamConstants.START_ELEMENT) {
          if (!"FeatureCollection".equals(reader.getLocalName())) {
            logger.debug("Not a WFS feature collection: {}", reader.getName());
            return null;
          }
          String value = reader.getAttributeValue(null, "numberMatched");
          if (value == null && numberOfFeatures) {
            value = reader.getAttributeValue(null, "numberOfFeatures");
          }
          return parseCount(value);
        }
      }
      return null;
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
