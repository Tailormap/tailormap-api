/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import static nl.b3p.tailormap.api.TestRequestProcessor.setServletPath;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import nl.b3p.tailormap.api.annotation.PostgresIntegrationTest;
import nl.b3p.tailormap.api.util.Constants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junitpioneer.jupiter.Stopwatch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@PostgresIntegrationTest
@Execution(ExecutionMode.CONCURRENT)
@Stopwatch
class SearchControllerIntegrationTest implements Constants {
  @Value("${tailormap-api.base-path}")
  private String apiBasePath;

  @Autowired private MockMvc mockMvc;

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void searchPostgis() throws Exception {
    final String url =
        apiBasePath
            + "/app/default/layer/lyr:snapshot-geoserver:postgis:begroeidterreindeel/search";

    mockMvc
        .perform(
            get(url)
                .accept(MediaType.APPLICATION_JSON)
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
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void searchPostgisGroen() throws Exception {
    final String url =
        apiBasePath
            + "/app/default/layer/lyr:snapshot-geoserver:postgis:begroeidterreindeel/search";

    mockMvc
        .perform(
            get(url)
                .accept(MediaType.APPLICATION_JSON)
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
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void searchSQLServerStartAtItem10() throws Exception {
    final String url =
        apiBasePath + "/app/default/layer/lyr:snapshot-geoserver:sqlserver:wegdeel/search";

    mockMvc
        .perform(
            get(url)
                .accept(MediaType.APPLICATION_JSON)
                .with(setServletPath(url))
                .param("q", "verhard~")
                .param("start", "10"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.start").value(10))
        .andExpect(
            jsonPath("$.total").value(both(greaterThanOrEqualTo(100)).and(lessThanOrEqualTo(110))))
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
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void searchOracleLayerWithoutIndex() throws Exception {
    final String url =
        apiBasePath + "/app/default/layer/lyr:snapshot-geoserver:oracle:WATERDEEL/search";

    mockMvc
        .perform(
            get(url)
                .accept(MediaType.APPLICATION_JSON)
                .with(setServletPath(url))
                .param("q", "bestaan*"))
        .andExpect(status().isNotFound());
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void testLayerDoesNotExist() throws Exception {
    final String url =
        apiBasePath + "/app/default/layer/lyr:snapshot-geoserver:doesnotexist/search";

    mockMvc
        .perform(
            get(url)
                .accept(MediaType.APPLICATION_JSON)
                .with(setServletPath(url))
                .param("q", "bestaan*"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(
            jsonPath("$.message")
                .value("Application layer with id lyr:snapshot-geoserver:doesnotexist not found"));
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void testLayerWithoutFeatureType() throws Exception {
    final String url = apiBasePath + "/app/default/layer/lyr:snapshot-geoserver:BGT/search";

    mockMvc
        .perform(
            get(url)
                .accept(MediaType.APPLICATION_JSON)
                .with(setServletPath(url))
                .param("q", "bestaan*"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.message").value("Layer 'BGT' does not have a search index"));
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void testBadRequestQuery() throws Exception {
    final String url =
        apiBasePath
            + "/app/default/layer/lyr:snapshot-geoserver:postgis:begroeidterreindeel/search";

    mockMvc
        .perform(
            get(url)
                .accept(MediaType.APPLICATION_JSON)
                .with(setServletPath(url))
                .param("q", "bestaan*")
                .param("start", "-1"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.message").value("Error while searching with given query"));
  }
}
