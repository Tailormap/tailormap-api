/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.persistence.helper;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.tailormap.api.geotools.featuresources.JDBCFeatureSourceHelper;
import org.tailormap.api.geotools.featuresources.WFSFeatureSourceHelper;
import org.tailormap.api.persistence.TMFeatureSource;

@Service
public class TMFeatureSourceHelper {
  @Value("${tailormap-api.timeout}")
  private int timeout;

  public TMFeatureSource loadCapabilities(TMFeatureSource featureSource) throws IOException {
    switch (featureSource.getProtocol()) {
      case WFS -> new WFSFeatureSourceHelper().loadCapabilities(featureSource, timeout);
      case JDBC -> new JDBCFeatureSourceHelper().loadCapabilities(featureSource, timeout);
      default -> throw new UnsupportedOperationException("Unsupported protocol: " + featureSource.getProtocol());
    }
    return featureSource;
  }

  public TMFeatureSource createFeatureSource(TMFeatureSource featureSource)
      throws IOException, IllegalArgumentException {
    switch (featureSource.getProtocol()) {
      case WFS -> {
        if (StringUtils.isBlank(featureSource.getUrl())) {
          throw new IllegalArgumentException("WFS feature source requires a URL");
        }
        URI uri;
        try {
          uri = new URI(featureSource.getUrl());
        } catch (URISyntaxException | NullPointerException e) {
          throw new IllegalArgumentException("Invalid URI");
        }
        if (!"https".equals(uri.getScheme()) && !"http".equals(uri.getScheme())) {
          throw new IllegalArgumentException("Invalid URI scheme");
        }
      }
      case JDBC -> {
        if (featureSource.getJdbcConnection() == null) {
          throw new IllegalArgumentException("JDBC connection properties are required");
        }
        if (featureSource.getAuthentication() == null) {
          throw new IllegalArgumentException("Database username and password are required");
        }
      }
    }

    return loadCapabilities(featureSource);
  }
}
