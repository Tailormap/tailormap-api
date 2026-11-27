/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.geotools.featuresources;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class WFSFeatureSourceHelperTest {

  @ParameterizedTest(name = "#{index}: {0} -> {1}")
  @CsvSource({
    "https://example.com/wfs, 2.0.0",
    "https://example.com/wfs?, 2.0.0",
    "https://example.com/wfs?service=WFS, 2.0.0",
    "https://example.com/wfs?service=WFS&request=GetCapabilities, 2.0.0",
    "https://example.com/wfs?VERSION=2.0.0, 2.0.0",
    "https://example.com/wfs?service=WFS&VERSION=1.1.0, 1.1.0",
    "https://example.com/wfs?version=1.1.0, 1.1.0",
    "https://example.com/wfs?Version=1.0.0, 1.0.0",
    "https://example.com/wfs?VERSION=2.0, 2.0.0",
    "https://example.com/wfs?VERSION=3.0.0, 2.0.0",
    "https://example.com/wfs?VERSION=, 2.0.0",
  })
  void get_wfs_version(String url, String expectedVersion) {
    assertEquals(expectedVersion, WFSFeatureSourceHelper.getWFSVersion(url));
  }
}
