/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller.admin;

import static org.hamcrest.Matchers.closeTo;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.util.DefaultTimeZone;
import org.junitpioneer.jupiter.Stopwatch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.tailormap.api.annotation.PostgresIntegrationTest;
import org.tailormap.api.persistence.Group;
import org.tailormap.api.repository.FeatureSourceRepository;
import org.tailormap.api.repository.FeatureTypeRepository;

@PostgresIntegrationTest
@AutoConfigureMockMvc
@Execution(ExecutionMode.SAME_THREAD)
@Stopwatch
class AttributeStatisticsAdminControllerIntegrationTest {
  @Value("${tailormap-api.admin.base-path}/statistics/")
  private String adminStatsPath;

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private FeatureTypeRepository featureTypeRepository;

  @Autowired
  private FeatureSourceRepository featureSourceRepository;

  private long kad_perceelFeatureTypeId;
  private long bakFeatureTypeId;

  @BeforeEach
  void setup() {
    kad_perceelFeatureTypeId = featureTypeRepository
        .getTMFeatureTypeByNameAndFeatureSource(
            "kadastraal_perceel",
            featureSourceRepository
                .getByTitle("PostGIS")
                .orElseThrow(() -> new NoSuchElementException("PostGIS feature source not found")))
        .orElseThrow(() -> new NoSuchElementException("Feature type kadastraal_perceel not found"))
        .getId();
    assumeTrue(kad_perceelFeatureTypeId > 0, "Feature type id should be greater than 0");

    bakFeatureTypeId = featureTypeRepository
        .getTMFeatureTypeByNameAndFeatureSource(
            "bak",
            featureSourceRepository
                .getByTitle("PostGIS")
                .orElseThrow(() -> new NoSuchElementException("PostGIS feature source not found")))
        .orElseThrow(() -> new NoSuchElementException("Feature type bak not found"))
        .getId();
    assumeTrue(bakFeatureTypeId > 0, "Feature type id should be greater than 0");
  }

  @Test
  void unauthenticated_get_kadastraal_perceel_deltax_statistics() throws Exception {
    String url = adminStatsPath + kad_perceelFeatureTypeId + "/deltax";
    mockMvc.perform(get(url).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", "/api/unauthorized"));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  void get_kadastraal_perceel_deltax_statistics() throws Exception {
    mockMvc.perform(get(adminStatsPath + kad_perceelFeatureTypeId + "/deltax")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.count").value(4518))
        .andExpect(jsonPath("$.min").value(-37.862))
        .andExpect(jsonPath("$.max").value(24.861))
        .andExpect(jsonPath("$.sum").value(closeTo(-280.1140, 0.001)))
        .andExpect(jsonPath("$.avg").value(closeTo(BigDecimal.valueOf(-0.0619995), BigDecimal.valueOf(0.01))));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  @DefaultTimeZone("GMT")
  void get_bak_creationdate_statistics() throws Exception {
    mockMvc.perform(get(adminStatsPath + bakFeatureTypeId + "/creationdate").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.count").value(201))
        .andExpect(jsonPath("$.min").value("2017-01-26T00:00:00.000Z"))
        .andExpect(jsonPath("$.max").value("2022-02-23T00:00:00.000Z"));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  @DefaultTimeZone("GMT")
  void get_bak_tijdstipregistratie_statistics() throws Exception {
    mockMvc.perform(get(adminStatsPath + bakFeatureTypeId + "/tijdstipregistratie")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.count").value(201))
        .andExpect(jsonPath("$.min").value("2017-03-08T12:02:57.000Z"))
        .andExpect(jsonPath("$.max").value("2022-03-01T15:56:05.000Z"));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  @DefaultTimeZone("GMT")
  void get_bak_inonderzoek_statistics() throws Exception {
    mockMvc.perform(get(adminStatsPath + bakFeatureTypeId + "/inonderzoek").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON));
  }
}
