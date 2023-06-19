/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import static nl.b3p.tailormap.api.TestRequestProcessor.setServletPath;
import static nl.b3p.tailormap.api.persistence.Group.ADMIN;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import nl.b3p.tailormap.api.StaticTestData;
import nl.b3p.tailormap.api.annotation.PostgresIntegrationTest;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;
import org.junitpioneer.jupiter.Stopwatch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@PostgresIntegrationTest
@AutoConfigureMockMvc
@Stopwatch
@TestClassOrder(ClassOrderer.OrderAnnotation.class)
// should be last test to prevent side effects - as some data is deleted
@Order(Integer.MAX_VALUE)
class EditFeatureControllerIntegrationTest {
  /** bestuurlijke gebieden WFS; provincies . */
  private static final String provinciesWFS =
      "/app/default/layer/lyr:pdok-kadaster-bestuurlijkegebieden:Provinciegebied/edit/feature/"
          + StaticTestData.get("utrecht__fid");

  private static final String begroeidterreindeelUrlPostgis =
      "/app/default/layer/lyr:snapshot-geoserver:postgis:begroeidterreindeel/edit/feature/begroeidterreindeel.246012ba7dabba12affcd1d1f9905488";
  private static final String waterdeelUrlOracle =
      "/app/default/layer/lyr:snapshot-geoserver:oracle:WATERDEEL/edit/feature/WATERDEEL.acf07292f30f25acab44bbe613793961";
  private static final String wegdeelUrlSqlserver =
      "/app/default/layer/lyr:snapshot-geoserver:sqlserver:wegdeel/edit/feature/wegdeel.02b14ba0bcd96dde1508574063404de2";

  @Value("${tailormap-api.base-path}")
  private String apiBasePath;

  @Autowired private MockMvc mockMvc;

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void testUnAuthenticatedDelete() throws Exception {
    final String url = apiBasePath + begroeidterreindeelUrlPostgis;
    mockMvc
        .perform(delete(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(401))
        .andExpect(jsonPath("$.url").value("/login"));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {ADMIN})
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void testGet() throws Exception {
    final String url = apiBasePath + begroeidterreindeelUrlPostgis;
    mockMvc
        .perform(get(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
        .andExpect(status().is4xxClientError())
        .andExpect(status().is(405));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {ADMIN})
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void testPut() throws Exception {
    final String url = apiBasePath + begroeidterreindeelUrlPostgis;
    mockMvc
        .perform(put(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
        .andExpect(status().is4xxClientError())
        .andExpect(status().is(405));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {ADMIN})
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void testDeleteWhenLayerDoesNotExist() throws Exception {
    final String url =
        apiBasePath + "/app/default/layer/lyr:doesnotexist:doesnotexist/edit/feature/does.not.1";
    mockMvc
        .perform(delete(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
        .andExpect(status().is4xxClientError())
        .andExpect(status().is(404));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {ADMIN})
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void testDeleteNonExistentFeature() throws Exception {
    final String url = apiBasePath + begroeidterreindeelUrlPostgis + "xxxxxx";
    // geotools does not report back that the feature does not exist, nor the number of deleted
    // features, no error === success
    mockMvc
        .perform(delete(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
        .andExpect(status().is2xxSuccessful())
        .andExpect(status().is(204));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {ADMIN})
  void testDeleteExistingFeatureMsSql() throws Exception {
    final String url = apiBasePath + wegdeelUrlSqlserver;
    mockMvc
        .perform(delete(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
        .andExpect(status().is2xxSuccessful())
        .andExpect(status().is(204));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {ADMIN})
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void testDeleteExistingFeaturePG() throws Exception {
    final String url = apiBasePath + begroeidterreindeelUrlPostgis;
    mockMvc
        .perform(delete(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
        .andExpect(status().is2xxSuccessful())
        .andExpect(status().is(204));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {ADMIN})
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void testDeleteExistingFeatureOrcl() throws Exception {
    final String url = apiBasePath + waterdeelUrlOracle;
    mockMvc
        .perform(delete(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
        .andExpect(status().is2xxSuccessful())
        .andExpect(status().is(204));
  }
}
