/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller;

import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWithIgnoringCase;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.tailormap.api.TestRequestProcessor.setServletPath;
import static org.tailormap.api.controller.TestUrls.layerBegroeidTerreindeelPostgis;
import static org.tailormap.api.controller.TestUrls.layerWaterdeelOracle;
import static org.tailormap.api.controller.TestUrls.layerWegdeelSqlServer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junitpioneer.jupiter.Stopwatch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.tailormap.api.annotation.PostgresIntegrationTest;
import org.tailormap.api.util.Constants;

@AutoConfigureMockMvc
@PostgresIntegrationTest
@Execution(ExecutionMode.CONCURRENT)
@Stopwatch
class SearchControllerIntegrationTest implements Constants {
  @Value("${tailormap-api.base-path}")
  private String apiBasePath;

  @Autowired
  private MockMvc mockMvc;

  @Test
  void searchPostgis() throws Exception {
    final String url = apiBasePath + layerBegroeidTerreindeelPostgis + "/search";

    mockMvc.perform(get(url).accept(MediaType.APPLICATION_JSON)
            .with(setServletPath(url))
            .param("q", "*")
            .param("start", "0"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.start").value(0))
        .andExpect(jsonPath("$.total").value(both(greaterThan(3660)).and(lessThanOrEqualTo(3665))))
        .andExpect(jsonPath("$.documents").isArray())
        .andExpect(jsonPath("$.documents.length()").value(10))
        .andExpect(jsonPath("$.documents[0].fid").isString())
        .andExpect(jsonPath("$.documents[0].fid").value(not(contains("wegdeel"))))
        .andExpect(jsonPath("$.documents[0].displayValues").isArray())
        .andExpect(jsonPath("$.documents[0]." + INDEX_GEOM_FIELD).isString())
        // wildcard search == filter, so maxScore is not set
        // .andExpect(jsonPath("$.maxScore").value(closeTo(1.0, 0.001)))
        .andExpect(jsonPath("$.maxScore").isEmpty());
  }

  @Test
  void searchPostgisGroen() throws Exception {
    final String url = apiBasePath + layerBegroeidTerreindeelPostgis + "/search";

    mockMvc.perform(get(url).accept(MediaType.APPLICATION_JSON)
            .with(setServletPath(url))
            .param("q", "groen*")
            .param("start", "0"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.start").value(0))
        .andExpect(jsonPath("$.total").value(both(greaterThan(3550)).and(lessThanOrEqualTo(3554))))
        .andExpect(jsonPath("$.documents").isArray())
        .andExpect(jsonPath("$.documents.length()").value(10))
        .andExpect(jsonPath("$.documents[0].fid").isString())
        .andExpect(jsonPath("$.documents[0].fid").value(not(contains("wegdeel"))))
        .andExpect(jsonPath("$.documents[0].displayValues").isArray())
        .andExpect(jsonPath("$.documents[0]." + INDEX_GEOM_FIELD).isString())
        // wildcard search == filter, so maxScore is not set
        .andExpect(jsonPath("$.maxScore").isEmpty())
    // .andExpect(jsonPath("$.maxScore").value(closeTo(1.0, 0.1)))
    ;
  }

  @Test
  void searchSQLServerStartAtItem10() throws Exception {
    final String url = apiBasePath + layerWegdeelSqlServer + "/search";

    mockMvc.perform(get(url).accept(MediaType.APPLICATION_JSON)
            .with(setServletPath(url))
            .param("q", "verhard~")
            .param("start", "10"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.start").value(10))
        .andExpect(
            jsonPath("$.total").value(both(greaterThanOrEqualTo(50)).and(lessThanOrEqualTo(110))))
        // .andExpect(jsonPath("$.maxScore").isNumber())
        // .andExpect(jsonPath("$.maxScore").value(closeTo(1.0, 0.1)))
        .andExpect(jsonPath("$.maxScore").isEmpty())
        .andExpect(jsonPath("$.documents").isArray())
        .andExpect(jsonPath("$.documents.length()").value(10))
        .andExpect(jsonPath("$.documents[0].fid").isString())
        .andExpect(jsonPath("$.documents[0].fid").value(not(contains("begroeidterreindeel"))))
        .andExpect(jsonPath("$.documents[0].displayValues").isArray())
        .andExpect(jsonPath("$.documents[0]." + INDEX_GEOM_FIELD).isString());
  }

  @Test
  void searchOracleLayerWithoutIndex() throws Exception {
    final String url = apiBasePath + layerWaterdeelOracle + "/search";

    mockMvc.perform(get(url).accept(MediaType.APPLICATION_JSON)
            .with(setServletPath(url))
            .param("q", "bestaan*"))
        .andExpect(status().isNotFound());
  }

  @Test
  void testLayerDoesNotExist() throws Exception {
    final String url = apiBasePath + "/app/default/layer/lyr:snapshot-geoserver:doesnotexist/search";

    mockMvc.perform(get(url).accept(MediaType.APPLICATION_JSON)
            .with(setServletPath(url))
            .param("q", "bestaan*"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.message")
            .value("Application layer with id lyr:snapshot-geoserver:doesnotexist not found"));
  }

  @Test
  void testLayerWithoutFeatureType() throws Exception {
    final String url = apiBasePath + "/app/default/layer/lyr:snapshot-geoserver:BGT/search";

    mockMvc.perform(get(url).accept(MediaType.APPLICATION_JSON)
            .with(setServletPath(url))
            .param("q", "bestaan*"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.message").value("Layer 'BGT' does not have a search index"));
  }

  @Test
  void testBadRequestQuery() throws Exception {
    final String url = apiBasePath + layerBegroeidTerreindeelPostgis + "/search";

    mockMvc.perform(get(url).accept(MediaType.APPLICATION_JSON)
            .with(setServletPath(url))
            .param("q", "bestaan*")
            .param("start", "-1"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.message").value("Error while searching with given query"));
  }

  @Test
  void testSpatialQueryDistance() throws Exception {
    final String url = apiBasePath + layerWegdeelSqlServer + "/search";

    mockMvc.perform(get(url).accept(MediaType.APPLICATION_JSON)
            .with(setServletPath(url))
            .param("q", "open")
            .param("start", "0")
            // added in backend
            // .param("fq", "{!geofilt sfield=geometry}")
            .param("pt", "133809 458811")
            .param("d", "0.005"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.start").value(0))
        .andExpect(jsonPath("$.total").value(2))
        .andExpect(jsonPath("$.documents").isArray())
        .andExpect(jsonPath("$.documents.length()").value(2))
        .andExpect(jsonPath("$.documents[0].fid").isString())
        .andExpect(jsonPath("$.documents[0].fid").value(startsWithIgnoringCase("wegdeel")))
        .andExpect(jsonPath("$.documents[0].displayValues").isArray())
        .andExpect(jsonPath("$.documents[0]." + INDEX_GEOM_FIELD).isString());
  }

  @Test
  void testSpatialQueryDistanceWithBbox() throws Exception {
    final String url = apiBasePath + layerWegdeelSqlServer + "/search";

    mockMvc.perform(get(url).accept(MediaType.APPLICATION_JSON)
            .with(setServletPath(url))
            .param("q", "open")
            .param("start", "0")
            .param("fq", "{!bbox sfield=geometry}")
            .param("pt", "133809 458811")
            .param("d", "0.5"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.start").value(0))
        .andExpect(jsonPath("$.total").value(2))
        .andExpect(jsonPath("$.documents").isArray())
        .andExpect(jsonPath("$.documents.length()").value(2))
        .andExpect(jsonPath("$.documents[0].fid").isString())
        .andExpect(jsonPath("$.documents[0].fid").value(startsWithIgnoringCase("wegdeel")))
        .andExpect(jsonPath("$.documents[0].displayValues").isArray())
        .andExpect(jsonPath("$.documents[0]." + INDEX_GEOM_FIELD).isString());
  }
}
