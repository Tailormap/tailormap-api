/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.geotools.wfs;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.geotools.http.HTTPClient;
import org.tailormap.api.geotools.ResponseTeeingHTTPClient;

/**
 * A {@link ResponseTeeingHTTPClient} which keeps the start of the most recent responses, not only of the latest. When
 * GeoTools parses a GetFeature response it can request the DescribeFeatureType from the {@code xsi:schemaLocation}, so
 * the latest response is not necessarily the response with the features.
 */
public class RecentResponsesHTTPClient extends ResponseTeeingHTTPClient {
  private static final int MAX_RECENT_RESPONSES = 8;

  private final Deque<TeeHTTPResponseWrapper> recentResponses;

  /**
   * @param wrapped the GeoTools HTTPClient to wrap
   * @param maxCopySize the maximum number of bytes of each response body to keep
   */
  public RecentResponsesHTTPClient(HTTPClient wrapped, int maxCopySize) {
    this(wrapped, new ArrayDeque<>(), maxCopySize);
  }

  private RecentResponsesHTTPClient(
      HTTPClient wrapped, Deque<TeeHTTPResponseWrapper> recentResponses, int maxCopySize) {
    super(
        wrapped,
        (url, response) -> {
          synchronized (recentResponses) {
            recentResponses.addLast(response);
            while (recentResponses.size() > MAX_RECENT_RESPONSES) {
              recentResponses.removeFirst();
            }
          }
        },
        null,
        maxCopySize);
    this.recentResponses = recentResponses;
  }

  /**
   * Returns the most recent responses.
   *
   * @return the responses, oldest first
   */
  public List<TeeHTTPResponseWrapper> getRecentResponses() {
    synchronized (recentResponses) {
      return List.copyOf(recentResponses);
    }
  }
}
