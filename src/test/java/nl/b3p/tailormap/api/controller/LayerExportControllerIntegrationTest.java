/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import static nl.b3p.tailormap.api.TestRequestProcessor.setServletPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import nl.b3p.tailormap.api.annotation.PostgresIntegrationTest;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junitpioneer.jupiter.Stopwatch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@PostgresIntegrationTest
@AutoConfigureMockMvc
@Execution(ExecutionMode.CONCURRENT)
@Stopwatch
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LayerExportControllerPostgresIntegrationTest {

  private static final String waterdeelUrlOracle =
      "/app/default/layer/lyr:snapshot-geoserver:oracle:WATERDEEL/export/download";
  private static final String pdokProvincies =
      "/app/default/layer/lyr:pdok-kadaster-bestuurlijkegebieden:Provinciegebied/export/download";

  @Autowired private MockMvc mockMvc;

  @Value("${tailormap-api.base-path}")
  private String apiBasePath;

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void shouldReturnExportCapabilitiesWithJDBCFeatureSource() throws Exception {
    final String url = apiBasePath + waterdeelUrlOracle.replace("download", "capabilities");
    mockMvc
        .perform(get(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.exportable").value(Boolean.TRUE))
        .andExpect(
            jsonPath("$.outputFormats")
                .value(
                    Matchers.containsInAnyOrder(
                        "text/xml; subtype=gml/3.1.1",
                        "DXF",
                        "DXF-ZIP",
                        "GML2",
                        "KML",
                        "SHAPE-ZIP",
                        "application/geopackage+sqlite3",
                        "application/gml+xml; version=3.2",
                        "application/json",
                        "application/vnd.google-earth.kml xml",
                        "application/vnd.google-earth.kml+xml",
                        "application/x-gpkg",
                        "csv",
                        "excel",
                        "excel2007",
                        "geopackage",
                        "geopkg",
                        "gml3",
                        "gml32",
                        "gpkg",
                        "json",
                        "text/csv",
                        "text/xml; subtype=gml/2.1.2",
                        "text/xml; subtype=gml/3.2")));
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void shouldReturnExportCapabilitiesWithWFSFeatureSource() throws Exception {
    final String url = apiBasePath + pdokProvincies.replace("download", "capabilities");
    mockMvc
        .perform(get(url).with(setServletPath(url)).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.exportable").value(Boolean.TRUE))
        .andExpect(
            jsonPath("$.outputFormats")
                .value(
                    Matchers.containsInAnyOrder(
                        "text/xml; subtype=gml/3.1.1",
                        "application/json; subtype=geojson",
                        "application/json",
                        "text/xml")));
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void shouldExportGeoJSON() throws Exception {
    final String url = apiBasePath + pdokProvincies;
    mockMvc
        .perform(
            get(url)
                .with(setServletPath(url))
                .accept(MediaType.APPLICATION_JSON)
                .param("outputFormat", "application/json")
                .param("attributes", "geom,naam,code"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.type").value("FeatureCollection"))
        .andExpect(jsonPath("$.name").value("Provinciegebied"))
        .andExpect(jsonPath("$.features.length()").value(12))
        .andExpect(jsonPath("$.features[0].geometry.type").value("MultiPolygon"));
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void shouldExportGeoPackage() throws Exception {
    final String url = apiBasePath + waterdeelUrlOracle;
    mockMvc
        .perform(
            get(url)
                .accept(MediaType.APPLICATION_JSON)
                .with(setServletPath(url))
                .param("outputFormat", "application/geopackage+sqlite3"))
        .andExpect(status().isOk())
        .andExpect(content().contentType("application/geopackage+sqlite3"));
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void shouldExportGeoJSONWithFilter() throws Exception {
    final String url = apiBasePath + waterdeelUrlOracle;
    mockMvc
        .perform(
            get(url)
                .accept(MediaType.APPLICATION_JSON)
                .with(setServletPath(url))
                .param("outputFormat", MediaType.APPLICATION_JSON_VALUE)
                .param("filter", "(BRONHOUDER IN ('G1904'))"))
        .andExpect(status().isOk())
        // GeoServer returns application/json;charset=UTF-8; but this is deprecated
        // .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
        .andExpect(jsonPath("$.type").value("FeatureCollection"))
        .andExpect(jsonPath("$.features.length()").value(1))
        .andExpect(jsonPath("$.features[0].geometry.type").value("Polygon"))
        .andExpect(jsonPath("$.features[0].properties.BRONHOUDER").value("G1904"));
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void shouldExportGeoJSONWithFilterAndSort() throws Exception {
    final String url = apiBasePath + waterdeelUrlOracle;
    mockMvc
        .perform(
            get(url)
                .accept(MediaType.APPLICATION_JSON)
                .with(setServletPath(url))
                .param("outputFormat", "application/json")
                .param("filter", "(BRONHOUDER IN ('G1904','L0002','L0004'))")
                .param("sortBy", "CLASS")
                .param("sortOrder", "asc"))
        .andExpect(status().isOk())
        // GeoServer returns application/json;charset=UTF-8; but this is deprecated
        // .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
        .andExpect(jsonPath("$.type").value("FeatureCollection"))
        .andExpect(jsonPath("$.features.length()").value(19))
        .andExpect(jsonPath("$.features[0].geometry.type").value("Polygon"))
        .andExpect(jsonPath("$.features[0].properties.CLASS").value("greppel, droge sloot"));

    mockMvc
        .perform(
            get(url)
                .accept(MediaType.APPLICATION_JSON)
                .with(setServletPath(url))
                .param("outputFormat", "application/json")
                .param("filter", "(BRONHOUDER IN ('G1904','L0002','L0004'))")
                .param("sortBy", "CLASS")
                .param("sortOrder", "desc"))
        .andExpect(status().isOk())
        // GeoServer returns application/json;charset=UTF-8; but this is deprecated
        // .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
        .andExpect(jsonPath("$.type").value("FeatureCollection"))
        .andExpect(jsonPath("$.features.length()").value(19))
        .andExpect(jsonPath("$.features[0].geometry.type").value("Polygon"))
        .andExpect(jsonPath("$.features[0].properties.CLASS").value("watervlakte"));
  }
}
