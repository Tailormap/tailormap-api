/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.persistence.helper;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tailormap.api.admin.model.CapabilitiesLoadingEvent;
import org.tailormap.api.persistence.TMFeatureSource;
import org.tailormap.api.persistence.json.JDBCConnectionProperties;

class TMFeatureSourceHelperTest {
  private TMFeatureSourceHelper tmFeatureSourceHelper;

  @BeforeEach
  void setUp() {
    tmFeatureSourceHelper = new TMFeatureSourceHelper(null, null) {
      @Override
      public void reportCapabilitiesLoadingProgress(CapabilitiesLoadingEvent event) {
        // Do nothing, not needed for these tests
      }
    };
  }

  @Test
  void should_throw_exception_for_blank_wfs_url() {
    TMFeatureSource featureSource =
        new TMFeatureSource().setProtocol(TMFeatureSource.Protocol.WFS).setUrl("");
    assertThrows(IllegalArgumentException.class, () -> tmFeatureSourceHelper.createFeatureSource(featureSource));
  }

  @Test
  void should_throw_exception_for_invalid_wfs_url() {
    TMFeatureSource featureSource =
        new TMFeatureSource().setProtocol(TMFeatureSource.Protocol.WFS).setUrl("invalid-url");
    assertThrows(IllegalArgumentException.class, () -> tmFeatureSourceHelper.createFeatureSource(featureSource));
  }

  @Test
  void should_throw_exception_for_invalid_uri() {
    TMFeatureSource featureSource =
        new TMFeatureSource().setProtocol(TMFeatureSource.Protocol.WFS).setUrl("ftp://invalid-url");
    assertThrows(IllegalArgumentException.class, () -> tmFeatureSourceHelper.createFeatureSource(featureSource));
  }

  @Test
  void should_throw_exception_for_missing_connection_properties() {
    TMFeatureSource featureSource = new TMFeatureSource()
        .setProtocol(TMFeatureSource.Protocol.JDBC)
        .setJdbcConnection(null)
        .setAuthentication(null);
    assertThrows(IllegalArgumentException.class, () -> tmFeatureSourceHelper.createFeatureSource(featureSource));
  }

  @Test
  void should_throw_exception_for_missing_authentication_properties() {
    TMFeatureSource featureSource = new TMFeatureSource()
        .setProtocol(TMFeatureSource.Protocol.JDBC)
        .setJdbcConnection(new JDBCConnectionProperties())
        .setAuthentication(null);
    assertThrows(IllegalArgumentException.class, () -> tmFeatureSourceHelper.createFeatureSource(featureSource));
  }
}
