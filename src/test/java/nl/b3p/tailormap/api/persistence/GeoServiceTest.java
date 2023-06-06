/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class GeoServiceTest {

  private GeoService geoService;

  static Stream<Arguments> urlProvider() {
    return Stream.of(
        arguments("https://geoserver.nl/wms", "https://geoserver.nl/wms"),
        arguments(
            "https://geoserver.nl/wms?encoded=aapjes%20%3D%20!%263%3D1",
            "https://geoserver.nl/wms?encoded=aapjes%20%3D%20!%263%3D1"),
        arguments("https://geoserver.nl/wms?", "https://geoserver.nl/wms"),
        arguments("https://geoserver.nl/wms?request=getcapabilities", "https://geoserver.nl/wms"),
        arguments(
            "https://geoserver.nl/wms?request=getcapabilities&REQUEST=getMap",
            "https://geoserver.nl/wms"),
        arguments("https://geoserver.nl/wmts?REQUEST=getcapabilities", "https://geoserver.nl/wmts"),
        arguments(
            "https://geoserver.nl/wms?request=getcapabilities&version=1",
            "https://geoserver.nl/wms?version=1"),
        arguments("https://geoserver.nl/wms?&version=1", "https://geoserver.nl/wms?version=1"),
        arguments("https://geoserver.nl/wms?&version=1,2", "https://geoserver.nl/wms?version=1,2"),
        arguments(
            "https://service.pdok.nl/hwh/luchtfotorgb/wmts/v1_0",
            "https://service.pdok.nl/hwh/luchtfotorgb/wmts/v1_0"),
        arguments(
            "https://basemap.at/wmts/1.0.0/WMTSCapabilities.xml",
            "https://basemap.at/wmts/1.0.0/WMTSCapabilities.xml"),
        arguments(
            "https://service.pdok.nl/kadaster/bestuurlijkegebieden/wms/v1_0?service=WMS",
            "https://service.pdok.nl/kadaster/bestuurlijkegebieden/wms/v1_0?service=WMS"),
        arguments(
            "https://service.pdok.nl/kadaster/bestuurlijkegebieden/wms/v1_0?service=WMS&request=getMap",
            "https://service.pdok.nl/kadaster/bestuurlijkegebieden/wms/v1_0?service=WMS"));
  }

  @BeforeEach
  void setGeoService() {
    geoService = new GeoService();
  }

  @ParameterizedTest(name = "#{index}: should sanitise url: {0}")
  @MethodSource("urlProvider")
  void testSetUrl(final String input, final String expected) {
    geoService.setUrl(input);
    assertEquals(
        expected,
        geoService.getUrl(),
        () -> input + "not sanitised properly, expected " + expected);
  }
}
