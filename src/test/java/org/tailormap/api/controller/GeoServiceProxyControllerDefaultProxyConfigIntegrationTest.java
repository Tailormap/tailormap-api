/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.tailormap.api.TestRequestProcessor.setServletPath;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.tailormap.api.annotation.PostgresIntegrationTest;

@PostgresIntegrationTest
@AutoConfigureMockMvc
@Execution(ExecutionMode.CONCURRENT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(
    properties = {
      // Use the default proxy configuration, which denies layer patterns,
      // to test the default configuration of the GeoServiceProxyController
      "tailormap-api.proxy.passthrough.hostnames=",
      "tailormap-api.proxy.passthrough.layerpatterns="
    })
class GeoServiceProxyControllerDefaultProxyConfigIntegrationTest {
  private final String begroeidterreindeelUrl =
      "/app/default/layer/lyr:snapshot-geoserver-proxied:postgis:begroeidterreindeel/proxy/wms";

  @Autowired
  private WebApplicationContext context;

  private MockMvc mockMvc;

  @Value("${tailormap-api.base-path}")
  private String apiBasePath;

  @BeforeAll
  void initialize() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  @Test
  void post_request() throws Exception {
    final String path = apiBasePath + begroeidterreindeelUrl;
    mockMvc.perform(post(path)
            .param("REQUEST", "GetMap")
            .param("SERVICE", "WMS")
            .param("VERSION", "1.3.0")
            .param("FORMAT", "image/png")
            .param("STYLES", "")
            .param("TRANSPARENT", "TRUE")
            .param("LAYERS", "postgis:begroeidterreindeel")
            .param("WIDTH", "2775")
            .param("HEIGHT", "1002")
            .param("CRS", "EPSG:28992")
            .param("BBOX", "130574.85495843932,457818.25613033347,133951.6192003861,459037.5418133715")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .with(setServletPath(path)))
        .andExpect(status().isOk());
  }

  @Test
  void get_request() throws Exception {
    final String path = apiBasePath + begroeidterreindeelUrl;
    mockMvc.perform(get(path)
            .param("REQUEST", "GetMap")
            .param("SERVICE", "WMS")
            .param("VERSION", "1.3.0")
            .param("FORMAT", "image/png")
            .param("STYLES", "")
            .param("TRANSPARENT", "TRUE")
            .param("LAYERS", "postgis:begroeidterreindeel")
            .param("WIDTH", "2775")
            .param("HEIGHT", "1002")
            .param("CRS", "EPSG:28992")
            .param("BBOX", "130574.85495843932,457818.25613033347,133951.6192003861,459037.5418133715")
            .with(setServletPath(path)))
        .andExpect(status().isOk());
  }

  @Test
  void disallow_invalid_layer_name_param() throws Exception {
    final String path = apiBasePath + begroeidterreindeelUrl;
    mockMvc.perform(post(path)
            .param("REQUEST", "GetMap")
            .param("SERVICE", "WMS")
            .param("LAYERS", "postgis:invalid_layer_name")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .with(setServletPath(path)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Requested layer name does not match expected layer"));
  }

  @Test
  void disallow_two_layer_names_param() throws Exception {
    final String path = apiBasePath + begroeidterreindeelUrl;
    mockMvc.perform(post(path)
            .param("REQUEST", "GetMap")
            .param("SERVICE", "WMS")
            .param("LAYERS", "postgis:invalid_layer_name,postgis:begroeidterreindeel")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .with(setServletPath(path)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Multiple layers in LAYERS parameter not supported"));
  }
}
