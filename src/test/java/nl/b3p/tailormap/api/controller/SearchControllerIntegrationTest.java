/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import static nl.b3p.tailormap.api.TestRequestProcessor.setServletPath;
import static nl.b3p.tailormap.api.util.Constants.FID;
import static nl.b3p.tailormap.api.util.Constants.INDEX_GEOM_FIELD;
import static nl.b3p.tailormap.api.util.Constants.LAYER_NAME;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import nl.b3p.tailormap.api.annotation.PostgresIntegrationTest;
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
class SearchControllerIntegrationTest {
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
        .andExpect(jsonPath("$[0]").isMap())
        .andExpect(jsonPath("$[0]." + FID + "[0]").isString())
        .andExpect(jsonPath("$[0]." + INDEX_GEOM_FIELD + "[0]").isString())
        // see SolrUtil#getLayerName
        .andExpect(
            jsonPath("$[0]." + LAYER_NAME + "[0]")
                .value("snapshot-geoserver__postgis__begroeidterreindeel"));
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void searchOracle() throws Exception {
    final String url =
        apiBasePath + "/app/default/layer/lyr:snapshot-geoserver:oracle:WATERDEEL/search";

    mockMvc
        .perform(
            get(url)
                .accept(MediaType.APPLICATION_JSON)
                .with(setServletPath(url))
                .param("q", "bestaan*"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$[0]").isMap())
        .andExpect(jsonPath("$[0]." + FID + "[0]").isString())
        .andExpect(jsonPath("$[0]." + INDEX_GEOM_FIELD + "[0]").isString())
        // see SolrUtil#getLayerName
        .andExpect(
            jsonPath("$[0]." + LAYER_NAME + "[0]").value("snapshot-geoserver__oracle__WATERDEEL"));
  }
}
