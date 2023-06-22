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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
      "/app/default/layer/lyr:snapshot-geoserver:postgis:begroeidterreindeel/edit/feature/";
  private static final String waterdeelUrlOracle =
      "/app/default/layer/lyr:snapshot-geoserver:oracle:WATERDEEL/edit/feature/";
  private static final String wegdeelUrlSqlserver =
      "/app/default/layer/lyr:snapshot-geoserver:sqlserver:wegdeel/edit/feature/";

  @Value("${tailormap-api.base-path}")
  private String apiBasePath;

  @Autowired private MockMvc mockMvc;

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void testUnAuthenticatedDelete() throws Exception {
    final String url =
        apiBasePath
            + begroeidterreindeelUrlPostgis
            + StaticTestData.get("begroeidterreindeel__fid_delete");
    mockMvc
        .perform(delete(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(401))
        .andExpect(jsonPath("$.url").value("/login"));
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void testUnAuthenticatedPost() throws Exception {
    final String url = apiBasePath + begroeidterreindeelUrlPostgis + "some.fid.1234";

    mockMvc
        .perform(
            post(url)
                .accept(MediaType.APPLICATION_JSON)
                .with(setServletPath(url))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"geom\":null, \"attributes\":{\"case\":\"irrelevant\"}}"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(401))
        .andExpect(jsonPath("$.url").value("/login"));
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void testUnAuthenticatedPost2() throws Exception {
    final String url = apiBasePath + begroeidterreindeelUrlPostgis;

    mockMvc
        .perform(
            post(url)
                .accept(MediaType.APPLICATION_JSON)
                .with(setServletPath(url))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"geom\":null, \"attributes\":{\"case\":\"irrelevant\"}}"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(401))
        .andExpect(jsonPath("$.url").value("/login"));
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void testUnAuthenticatedPatch() throws Exception {
    final String url =
        apiBasePath
            + begroeidterreindeelUrlPostgis
            + StaticTestData.get("begroeidterreindeel__fid_edit");
    mockMvc
        .perform(
            patch(url)
                .accept(MediaType.APPLICATION_JSON)
                .with(setServletPath(url))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"properties\":{\"naam\":\"test\"}}"))
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
  void testPatchNonExistentAttribute() throws Exception {
    final String url =
        apiBasePath
            + begroeidterreindeelUrlPostgis
            + StaticTestData.get("begroeidterreindeel__fid_edit");
    mockMvc
        .perform(
            patch(url)
                .accept(MediaType.APPLICATION_JSON)
                .with(setServletPath(url))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"attributes\":{\"doesnotexist\":true}}"))
        .andExpect(status().is4xxClientError())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(400))
        .andExpect(
            jsonPath("$.message")
                .value(
                    "Feature cannot be edited, one or more requested attributes are not available on the feature type"));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {ADMIN})
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void testPatchPG() throws Exception {
    final String url =
        apiBasePath
            + begroeidterreindeelUrlPostgis
            + StaticTestData.get("begroeidterreindeel__fid_edit");
    mockMvc
        .perform(
            patch(url)
                .accept(MediaType.APPLICATION_JSON)
                .with(setServletPath(url))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"__fid\": \""
                        + StaticTestData.get("begroeidterreindeel__fid_edit")
                        + "\",\"attributes\" : { \"inonderzoek\":true, \"class\": \"weggemaaid grasland\", \"geom\" : \""
                        + StaticTestData.get("begroeidterreindeel__geom_edit")
                        + "\"}}"))
        .andExpect(status().is2xxSuccessful())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.__fid").value(StaticTestData.get("begroeidterreindeel__fid_edit")))
        .andExpect(
            jsonPath("$.geometry").value(StaticTestData.get("begroeidterreindeel__geom_edit")))
        .andExpect(jsonPath("$.attributes.inonderzoek").value(true))
        .andExpect(
            jsonPath("$.attributes.geom")
                .value(StaticTestData.get("begroeidterreindeel__geom_edit")))
        .andExpect(jsonPath("$.attributes.geom_kruinlijn").isEmpty())
        .andExpect(jsonPath("$.attributes.class").value("weggemaaid grasland"));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {ADMIN})
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void testPatchOrcl() throws Exception {
    final String url = apiBasePath + waterdeelUrlOracle + StaticTestData.get("waterdeel__fid_edit");
    mockMvc
        .perform(
            patch(url)
                .accept(MediaType.APPLICATION_JSON)
                .with(setServletPath(url))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"attributes\":{\"INONDERZOEK\":true,\"CLASS\":\"woeste bergbeek\",\"GEOM\":\""
                        + StaticTestData.get("waterdeel__edit_geom")
                        + "\"}}"))
        .andExpect(status().is2xxSuccessful())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.__fid").value(StaticTestData.get("waterdeel__fid_edit")))
        .andExpect(jsonPath("$.geometry").isNotEmpty())
        .andExpect(jsonPath("$.attributes.GEOM").value(StaticTestData.get("waterdeel__edit_geom")))
        .andExpect(jsonPath("$.attributes.INONDERZOEK").value("true"))
        .andExpect(jsonPath("$.attributes.CLASS").value("woeste bergbeek"));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {ADMIN})
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void testPatchMsSql() throws Exception {
    final String url = apiBasePath + wegdeelUrlSqlserver + StaticTestData.get("wegdeel__fid_edit");
    mockMvc
        .perform(
            patch(url)
                .accept(MediaType.APPLICATION_JSON)
                .with(setServletPath(url))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"attributes\":{\"inonderzoek\":true,\"surfacematerial\":\"weggemaaid grasland\",\"geom\":\""
                        + StaticTestData.get("wegdeel__geom_edit")
                        + "\"}}"))
        .andExpect(status().is2xxSuccessful())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.__fid").value(StaticTestData.get("wegdeel__fid_edit")))
        .andExpect(jsonPath("$.geometry").value(StaticTestData.get("wegdeel__geom_edit")))
        .andExpect(jsonPath("$.attributes.geom").value(StaticTestData.get("wegdeel__geom_edit")))
        .andExpect(jsonPath("$.attributes.inonderzoek").value(true))
        .andExpect(jsonPath("$.attributes.surfacematerial").value("weggemaaid grasland"));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {ADMIN})
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void testPatchWFS() throws Exception {
    final String url = apiBasePath + provinciesWFS + StaticTestData.get("utrecht__fid");
    mockMvc
        .perform(
            patch(url)
                .content("{\"attributes\":{\"naam\": \"Utereg\",\"code\":\"11\"}}")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(setServletPath(url)))
        .andExpect(status().is5xxServerError());
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {ADMIN})
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void testGetIsUnsupported() throws Exception {
    final String url =
        apiBasePath
            + begroeidterreindeelUrlPostgis
            + StaticTestData.get("begroeidterreindeel__fid_edit");
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
  void testPutIsUnsupported() throws Exception {
    final String url =
        apiBasePath
            + begroeidterreindeelUrlPostgis
            + StaticTestData.get("begroeidterreindeel__fid_edit");
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
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void testDeleteExistingFeatureMsSql() throws Exception {
    final String url =
        apiBasePath + wegdeelUrlSqlserver + StaticTestData.get("wegdeel__fid_delete");
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
    final String url =
        apiBasePath
            + begroeidterreindeelUrlPostgis
            + StaticTestData.get("begroeidterreindeel__fid_delete");
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
    final String url =
        apiBasePath + waterdeelUrlOracle + StaticTestData.get("waterdeel__fid_delete");
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
  void testDeleteExistingFeatureWFS() throws Exception {
    final String url = apiBasePath + provinciesWFS + StaticTestData.get("utrecht__fid");
    mockMvc
        .perform(delete(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
        .andExpect(status().is4xxClientError())
        .andExpect(status().is(400));
  }
}
