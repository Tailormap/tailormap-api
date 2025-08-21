/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller;

import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.tailormap.api.TestRequestProcessor.setServletPath;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junitpioneer.jupiter.displaynamegenerator.ReplaceCamelCaseAndUnderscoreAndNumber;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.tailormap.api.annotation.PostgresIntegrationTest;
import org.tailormap.api.persistence.Group;

@AutoConfigureMockMvc
@PostgresIntegrationTest
@TestPropertySource(
    properties = {
      "management.endpoints.web.exposure.include=prometheus",
      "management.endpoint.prometheus.access=read_only",
      "management.prometheus.metrics.export.enabled=true",
      "management.prometheus.metrics.export.descriptions=true",
    })
@DisplayNameGeneration(ReplaceCamelCaseAndUnderscoreAndNumber.class)
@Execution(ExecutionMode.CONCURRENT)
class IngestMetricsControllerIntegrationTest {
  @Value("${tailormap-api.base-path}")
  private String apiBasePath;

  @Value("${management.endpoints.web.base-path}")
  private String managementBasePath;

  @Autowired
  private MockMvc mockMvc;

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  void testIngestSingleLayerSwitchedOnMetric() throws Exception {
    final String url = apiBasePath
        + "/app/default/metrics/ingest/lyr:snapshot-geoserver:postgis:begroeidterreindeel/tailormap_applayer_switched_on";
    mockMvc.perform(put(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
        .andExpect(status().isNoContent());

    // check the prometheus endpoint to see if the metric is registered
    final String actuatorUrl = managementBasePath + "/prometheus";
    mockMvc.perform(get(actuatorUrl)
            .param("includedNames", "tailormap_applayer_switched_on")
            .with(setServletPath(actuatorUrl))
            .accept(MediaType.TEXT_PLAIN))
        .andExpect(status().isOk())
        .andExpect(content().contentType("text/plain;version=0.0.4;charset=utf-8"))
        .andExpect(
            content()
                .string(
                    containsStringIgnoringCase(
                        """
tailormap_applayer_switched_on_total{appId="1",appLayerId="lyr:snapshot-geoserver:postgis:begroeidterreindeel",appName="default",appType="app"} 2.0""")));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  void testIngestMultipleLayerSwitchedOnMetric() throws Exception {
    final String url = apiBasePath
        + "/app/default/metrics/ingest/lyr:snapshot-geoserver:postgis:begroeidterreindeel,lyr:openbasiskaart:osm/tailormap_applayer_switched_on";
    mockMvc.perform(put(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
        .andExpect(status().isNoContent());

    // check the prometheus endpoint to see if the metric is published for both layers
    final String actuatorUrl = managementBasePath + "/prometheus";
    mockMvc.perform(get(actuatorUrl)
            .param("includedNames", "tailormap_applayer_switched_on")
            .with(setServletPath(actuatorUrl))
            .accept(MediaType.TEXT_PLAIN))
        .andExpect(status().isOk())
        .andExpect(content().contentType("text/plain;version=0.0.4;charset=utf-8"))
        .andExpect(
            content()
                .string(
                    containsStringIgnoringCase(
                        """
tailormap_applayer_switched_on_total{appId="1",appLayerId="lyr:snapshot-geoserver:postgis:begroeidterreindeel",appName="default",appType="app"}""")))
        .andExpect(
            content()
                .string(
                    containsStringIgnoringCase(
                        """
tailormap_applayer_switched_on_total{appId="1",appLayerId="lyr:openbasiskaart:osm",appName="default",appType="app"} 1.0""")));
  }

  @Test
  void testIngestDisallowedMetric() throws Exception {
    final String url = apiBasePath
        + "/app/default/metrics/ingest/lyr:snapshot-geoserver:postgis:begroeidterreindeel/not_allowed";
    mockMvc.perform(put(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void testIngestTestMetric() throws Exception {
    final String url = apiBasePath
        + "/app/default/metrics/ingest/lyr:snapshot-geoserver:postgis:begroeidterreindeel/test_metric";
    mockMvc.perform(put(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
        .andExpect(status().isNoContent());
  }

  @Test
  void testIngestMetricWithInvalidLayer() throws Exception {
    final String url =
        apiBasePath + "/app/default/metrics/ingest/lyr:service:does-not-exist/tailormap_applayer_switched_on";
    mockMvc.perform(put(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
        .andExpect(status().isNoContent());
  }

  @Test
  void testIngestMetricWithoutLayers() throws Exception {
    final String url = apiBasePath + "/app/default/metrics/ingest/tailormap_applayer_switched_on";
    mockMvc.perform(put(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
        .andExpect(status().is4xxClientError());
  }
}
