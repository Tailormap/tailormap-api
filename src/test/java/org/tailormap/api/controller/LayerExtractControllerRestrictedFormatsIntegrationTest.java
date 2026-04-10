/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.tailormap.api.TestRequestProcessor.setServletPath;
import static org.tailormap.api.controller.TestUrls.layerBegroeidTerreindeelPostgis;
import static org.tailormap.api.controller.TestUrls.layerProxiedWithAuthInPublicApp;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.tailormap.api.annotation.PostgresIntegrationTest;

/** These testcase run with a subset of the available formats. */
@PostgresIntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"tailormap-api.extract.allowed-outputformats=csv,shape"})
class LayerExtractControllerRestrictedFormatsIntegrationTest {
  private static final String formatsPath = "/extract/formats";
  private static final String extractPath = "/extract/";
  private static final String downloadPath = "/extract/download/";

  @Autowired
  private MockMvc mockMvc;

  @Value("${tailormap-api.base-path}")
  private String apiBasePath;

  @Test
  void list_supported_formats() throws Exception {
    final String extractUrl = apiBasePath + layerBegroeidTerreindeelPostgis + formatsPath;
    mockMvc.perform(get(extractUrl).accept(MediaType.APPLICATION_JSON).with(setServletPath(extractUrl)))
        .andExpect(status().isOk())
        .andExpect(result -> assertThat(result.getResponse().getContentAsString(), is("[\"csv\",\"shape\"]")));
  }

  @Test
  void invalid_output_format_should_return_bad_request_on_extract() throws Exception {
    final String validClientId = "invalid_output_format-" + System.nanoTime();
    final String sseUrl = apiBasePath + "/events/" + validClientId;
    mockMvc.perform(get(sseUrl)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .with(setServletPath(sseUrl))
            .acceptCharset(StandardCharsets.UTF_8))
        .andExpect(request().asyncStarted())
        .andReturn();

    final String extractUrl = apiBasePath + layerBegroeidTerreindeelPostgis + extractPath + validClientId;
    mockMvc.perform(post(extractUrl)
            .accept(MediaType.APPLICATION_JSON)
            .with(setServletPath(extractUrl))
            .with(csrf())
            .param("attributes", "")
            // disallowed through properties
            .param("outputFormat", "geojson")
            .acceptCharset(StandardCharsets.UTF_8)
            .characterEncoding(StandardCharsets.UTF_8)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED))
        .andExpect(status().isBadRequest())
        .andExpect(result ->
            assertThat(result.getResponse().getContentAsString(), containsString("Invalid output format")));
  }

  @Test
  void invalid_client_id_should_return_bad_request_on_extract() throws Exception {
    final String invalidClientId = "invalid-te$t-" + System.nanoTime();
    final String extractUrl = apiBasePath + layerBegroeidTerreindeelPostgis + extractPath + invalidClientId;
    mockMvc.perform(post(extractUrl)
            .accept(MediaType.APPLICATION_JSON)
            .with(setServletPath(extractUrl))
            .with(csrf())
            .param("attributes", "")
            .param("outputFormat", "csv")
            .acceptCharset(StandardCharsets.UTF_8)
            .characterEncoding(StandardCharsets.UTF_8)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED))
        .andExpect(status().isBadRequest())
        .andExpect(result ->
            assertThat(result.getResponse().getContentAsString(), containsString("Invalid clientId")));
  }

  @Test
  void invalid_download_id_should_return_bad_request_on_download() throws Exception {
    final String extractUrl = apiBasePath + layerBegroeidTerreindeelPostgis + downloadPath + "invalidDownloadId";
    mockMvc.perform(get(extractUrl)
            .accept(MediaType.APPLICATION_OCTET_STREAM)
            .with(setServletPath(extractUrl)))
        .andExpect(status().isNotFound())
        .andExpect(result -> assertThat(
            result.getResponse().getContentAsString(), containsString("Download file not found")));
  }

  @Test
  void wms_secured_proxy_not_in_public_app_should_be_forbidden() throws Exception {
    final String validClientId = "format-test-" + System.nanoTime();
    final String extractUrl = apiBasePath + layerProxiedWithAuthInPublicApp + extractPath + validClientId;

    mockMvc.perform(post(extractUrl)
            .accept(MediaType.APPLICATION_JSON)
            .with(setServletPath(extractUrl))
            .with(csrf())
            .param("attributes", "")
            .param("outputFormat", "csv")
            .acceptCharset(StandardCharsets.UTF_8)
            .characterEncoding(StandardCharsets.UTF_8)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED))
        .andDo(print())
        .andExpect(status().isForbidden());
  }
}
