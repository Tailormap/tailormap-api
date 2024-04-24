/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import static nl.b3p.tailormap.api.TestRequestProcessor.setServletPath;
import static nl.b3p.tailormap.api.persistence.Group.ADMIN;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.JsonPath;
import nl.b3p.tailormap.api.StaticTestData;
import nl.b3p.tailormap.api.annotation.PostgresIntegrationTest;
import nl.b3p.tailormap.api.geotools.processing.GeometryProcessor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junitpioneer.jupiter.Stopwatch;
import org.locationtech.jts.geom.Geometry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@PostgresIntegrationTest
@AutoConfigureMockMvc
@Stopwatch
@TestClassOrder(ClassOrderer.OrderAnnotation.class)
// should be last test to prevent side effects - as some data is deleted
@Order(Integer.MAX_VALUE)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EditFeatureControllerIntegrationTest {
  /** bestuurlijke gebieden WFS; provincies. */
  private static final String provinciesWFS =
      "/app/default/layer/lyr:pdok-kadaster-bestuurlijkegebieden:Provinciegebied/edit/feature/"
          + StaticTestData.get("utrecht__fid");

  private static final String begroeidterreindeelUrlPostgis =
      "/app/default/layer/lyr:snapshot-geoserver:postgis:begroeidterreindeel/edit/feature";
  private static final String osm_polygonUrlPostgis =
      "/app/default/layer/lyr:snapshot-geoserver:postgis:osm_polygon/edit/feature";

  private static final String begroeidterreindeelUrlPostgisNonEditable =
      "/app/default/layer/lyr:snapshot-geoserver-proxied:postgis:begroeidterreindeel/edit/feature";
  private static final String waterdeelUrlOracle =
      "/app/default/layer/lyr:snapshot-geoserver:oracle:WATERDEEL/edit/feature";
  private static final String wegdeelUrlSqlserver =
      "/app/default/layer/lyr:snapshot-geoserver:sqlserver:wegdeel/edit/feature";

  @Value("${tailormap-api.base-path}")
  private String apiBasePath;

  @Autowired private WebApplicationContext context;

  private MockMvc mockMvc;

  @BeforeAll
  void initialize() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void testUnAuthenticatedDelete() throws Exception {
    final String url =
        apiBasePath
            + begroeidterreindeelUrlPostgis
            + "/"
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
            + "/"
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
  void testNonEditablePatch() throws Exception {
    final String url =
        apiBasePath
            + begroeidterreindeelUrlPostgisNonEditable
            + "/"
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
                        + "\",\"attributes\" : { \"class\": \"weggemaaid grasland\", \"geom\" : \""
                        + StaticTestData.get("begroeidterreindeel__geom_edit")
                        + "\"}}"))
        .andExpect(status().is4xxClientError())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value("400"))
        .andExpect(jsonPath("$.message").value("Layer is not editable"));
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
            + "/"
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
  void testPatchHiddenAttribute() throws Exception {
    final String url =
        apiBasePath
            + begroeidterreindeelUrlPostgis
            + "/"
            + StaticTestData.get("begroeidterreindeel__fid_edit");

    ObjectMapper objectMapper = new ObjectMapper();
    ObjectNode body = objectMapper.createObjectNode();
    ObjectNode attributes = objectMapper.createObjectNode();
    attributes.put("terminationdate", "something");
    attributes.put("geom_kruinlijn", "LINESTRING(0 0, 1 1)");
    body.set("attributes", attributes);

    mockMvc
        .perform(
            patch(url)
                .accept(MediaType.APPLICATION_JSON)
                .with(setServletPath(url))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
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
  void testPatchHiddenAttributeInAppLayerSettings() throws Exception {
    final String url =
        apiBasePath
            + begroeidterreindeelUrlPostgis
            + "/"
            + StaticTestData.get("begroeidterreindeel__fid_edit");

    ObjectMapper objectMapper = new ObjectMapper();
    ObjectNode body = objectMapper.createObjectNode();
    ObjectNode attributes = objectMapper.createObjectNode();
    attributes.put("begroeidterreindeeloptalud", "something");
    body.set("attributes", attributes);

    mockMvc
        .perform(
            patch(url)
                .accept(MediaType.APPLICATION_JSON)
                .with(setServletPath(url))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
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
  void testPatchReadOnlyAttribute() throws Exception {
    final String url =
        apiBasePath
            + begroeidterreindeelUrlPostgis
            + "/"
            + StaticTestData.get("begroeidterreindeel__fid_edit");

    ObjectMapper objectMapper = new ObjectMapper();
    ObjectNode body = objectMapper.createObjectNode();
    ObjectNode attributes = objectMapper.createObjectNode();
    attributes.put("eindregistratie", "something");
    body.set("attributes", attributes);

    mockMvc
        .perform(
            patch(url)
                .accept(MediaType.APPLICATION_JSON)
                .with(setServletPath(url))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().is4xxClientError())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(400))
        .andExpect(
            jsonPath("$.message")
                .value(
                    "Feature cannot be edited, one or more requested attributes are not editable on the feature type"));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {ADMIN})
  @Order(1)
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void testPostPG() throws Exception {
    final String url = apiBasePath + begroeidterreindeelUrlPostgis;
    final String gmlid =
        "aaa"
            + StaticTestData.get("begroeidterreindeel__fid_edit")
                .substring(StaticTestData.get("begroeidterreindeel__fid_edit").indexOf('.') + 4);
    final String fid = "begroeidterreindeel." + gmlid;
    final String content =
        "{\"__fid\":\""
            + fid
            + "\",\"attributes\":{"
            + "\"gmlid\":\""
            + gmlid
            + "\","
            + "\"identificatie\":\"B3P."
            + gmlid
            + "\","
            + "\"lv_publicatiedatum\":\"2021-01-15T10:33:08.000+00:00\","
            + "\"creationdate\":\"2020-12-23\","
            + "\"tijdstipregistratie\":\"2021-01-15T07:00:12.000+00:00\","
            + "\"bronhouder\":\"B3P\","
            + "\"inonderzoek\":true, "
            + "\"relatievehoogteligging\":0,"
            + "\"bgt_status\":\"bestaand\","
            + "\"plus_status\":\"geenWaarde\","
            + "\"plus_fysiekvoorkomen\":\"waardeOnbekend\","
            + "\"class\":\"weggemaaid grasland\", "
            + "\"geom\":\""
            + StaticTestData.get("begroeidterreindeel__geom_edit")
            + "\"}}";

    mockMvc
        .perform(
            post(url)
                .accept(MediaType.APPLICATION_JSON)
                .with(setServletPath(url))
                .contentType(MediaType.APPLICATION_JSON)
                .content(content))
        .andExpect(status().is2xxSuccessful())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.__fid").value(fid))
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
  @Order(10)
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void testDuplicatePrimaryKeyPG() throws Exception {
    final String url = apiBasePath + begroeidterreindeelUrlPostgis;
    final String gmlid =
        "aaa"
            + StaticTestData.get("begroeidterreindeel__fid_edit")
                .substring(StaticTestData.get("begroeidterreindeel__fid_edit").indexOf('.') + 4);
    final String fid = "begroeidterreindeel." + gmlid;
    final String content =
        "{\"__fid\":\""
            + fid
            + "\",\"attributes\":{"
            + "\"gmlid\":\""
            + gmlid
            + "\","
            + "\"identificatie\":\"B3P."
            + gmlid
            + "\","
            + "\"tijdstipregistratie\":\"2021-01-15T07:00:12.000+00:00\","
            + "\"bronhouder\":\"B3P\","
            + "\"inonderzoek\":true, "
            + "\"relatievehoogteligging\":0,"
            + "\"bgt_status\":\"bestaand\","
            + "\"plus_status\":\"geenWaarde\","
            + "\"plus_fysiekvoorkomen\":\"waardeOnbekend\","
            + "\"class\":\"weggemaaid grasland\", "
            + "\"geom\":\""
            + StaticTestData.get("begroeidterreindeel__geom_edit")
            + "\"}}";

    mockMvc
        .perform(
            post(url)
                .accept(MediaType.APPLICATION_JSON)
                .with(setServletPath(url))
                .contentType(MediaType.APPLICATION_JSON)
                .content(content))
        .andExpect(status().is5xxServerError())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.message").exists());
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {ADMIN})
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void testPostMsSql() throws Exception {
    final String url = apiBasePath + wegdeelUrlSqlserver;
    final String gmlid =
        "b3p"
            + StaticTestData.get("wegdeel__fid_edit")
                .substring(StaticTestData.get("wegdeel__fid_edit").indexOf('.') + 4);
    final String fid = "wegdeel." + gmlid;
    final String content =
        "{\"__fid\":\""
            + fid
            + "\",\"attributes\":{"
            + "\"gmlid\":\""
            + gmlid
            + "\","
            + "\"identificatie\":\"B3P."
            + gmlid
            + "\","
            + " \"plus_functiewegdeel\":\"waardeOnbekend\","
            + " \"creationdate\":\"2016-12-06\","
            + " \"plus_status\":\"geenWaarde\","
            + " \"bronhouder\":\"B3P\","
            + " \"surfacematerial\":\"gesloten verharding\","
            + " \"plus_fysiekvoorkomenwegdeel\":\"waardeOnbekend\","
            + " \"geom\":\""
            + StaticTestData.get("wegdeel__geom_edit")
            + "\","
            + " \"geom_kruinlijn\":null,"
            + " \"bgt_status\":\"bestaand\","
            + " \"lv_publicatiedatum\":\"2019-11-15T20:18:09.000+00:00\","
            + " \"relatievehoogteligging\":0,"
            + " \"function_\":\"rijbaan autosnelweg\","
            + " \"wegdeeloptalud\":false,"
            + " \"inonderzoek\":true,"
            + " \"tijdstipregistratie\": \"2019-11-15T12:52:08.000+00:00\"}}";

    mockMvc
        .perform(
            post(url)
                .accept(MediaType.APPLICATION_JSON)
                .with(setServletPath(url))
                .contentType(MediaType.APPLICATION_JSON)
                .content(content))
        .andExpect(status().is2xxSuccessful())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.__fid").value(fid))
        .andExpect(jsonPath("$.geometry").value(StaticTestData.get("wegdeel__geom_edit")))
        .andExpect(jsonPath("$.attributes.inonderzoek").value(true))
        .andExpect(jsonPath("$.attributes.geom").value(StaticTestData.get("wegdeel__geom_edit")))
        .andExpect(jsonPath("$.attributes.geom_kruinlijn").isEmpty())
        .andExpect(jsonPath("$.attributes.function_").value("rijbaan autosnelweg"));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {ADMIN})
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void testPostOrcl() throws Exception {
    final String url = apiBasePath + waterdeelUrlOracle;
    final String gmlid =
        "b3p"
            + StaticTestData.get("waterdeel__fid_edit")
                .substring(StaticTestData.get("waterdeel__fid_edit").indexOf('.') + 4);
    final String fid = "WATERDEEL." + gmlid;
    final String content =
        "{\"__fid\":\""
            + fid
            + "\",\"attributes\":{"
            + "\"GMLID\":\""
            + gmlid
            + "\","
            + "\"IDENTIFICATIE\":\"B3P."
            + gmlid
            + "\","
            + "\"CREATIONDATE\":\"2016-12-06\","
            + "\"PLUS_STATUS\":\"geenWaarde\","
            + "\"BRONHOUDER\":\"B3P\","
            + "\"CLASS\": \"waterloop\","
            + "\"PLUS_TYPE\":\"waardeOnbekend\","
            + "\"GEOM\":\""
            + StaticTestData.get("waterdeel__edit_geom")
            + "\","
            + "\"BGT_STATUS\":\"bestaand\","
            + "\"LV_PUBLICATIEDATUM\":\"2019-11-15T20:18:09.000+00:00\","
            + "\"RELATIEVEHOOGTELIGGING\":0,"
            + "\"INONDERZOEK\":true,"
            + "\"TIJDSTIPREGISTRATIE\": \"2019-11-15T12:52:08.000+00:00\"}}";

    mockMvc
        .perform(
            post(url)
                .accept(MediaType.APPLICATION_JSON)
                .with(setServletPath(url))
                .contentType(MediaType.APPLICATION_JSON)
                .content(content))
        .andExpect(status().is2xxSuccessful())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.__fid").value(fid))
        .andExpect(jsonPath("$.geometry").value(StaticTestData.get("waterdeel__edit_geom")))
        .andExpect(jsonPath("$.attributes.INONDERZOEK").value("true"))
        .andExpect(jsonPath("$.attributes.GEOM").value(StaticTestData.get("waterdeel__edit_geom")))
        .andExpect(jsonPath("$.attributes.CLASS").value("waterloop"));
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
            + "/"
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
  void testPatchForeignCRSPG() throws Exception {
    final String url =
        apiBasePath + osm_polygonUrlPostgis + "/" + StaticTestData.get("osm_polygon__fid_edit");
    final MvcResult result =
        mockMvc
            .perform(
                patch(url)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(setServletPath(url))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"__fid\": \""
                            + StaticTestData.get("osm_polygon__fid_edit")
                            + "\",\"attributes\" : { \"building\":\"abandoned industrial complex\", \"way\" : \""
                            + StaticTestData.get("osm_polygon__geom_edit_28992")
                            + "\"}}"))
            .andExpect(status().is2xxSuccessful())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.__fid").value(StaticTestData.get("osm_polygon__fid_edit")))
            .andExpect(jsonPath("$.attributes.building").value("abandoned industrial complex"))
            .andExpect(jsonPath("$.attributes.z_order").value(0))
            .andReturn();

    // check geometry equality
    final String body = result.getResponse().getContentAsString();
    final Geometry inputGeometry =
        GeometryProcessor.wktToGeometry(StaticTestData.get("osm_polygon__geom_edit_28992"));
    final Geometry geometry = GeometryProcessor.wktToGeometry(JsonPath.read(body, "$.geometry"));
    assertTrue(geometry.equalsExact(inputGeometry, 1.0), "Geometry is not equal");
    final Geometry way = GeometryProcessor.wktToGeometry(JsonPath.read(body, "$.attributes.way"));
    assertTrue(way.equalsExact(inputGeometry, 1.0), "Geometry is not equal");
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {ADMIN})
  void testPostForeignCRSPG() throws Exception {
    final String __fid = StaticTestData.get("osm_polygon__fid_edit") + "1234";
    final String osm_id = __fid.replace("osm_polygon.", "");
    final String url = apiBasePath + osm_polygonUrlPostgis;
    final MvcResult result =
        mockMvc
            .perform(
                post(url)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(setServletPath(url))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"__fid\": \""
                            + __fid
                            + "\",\"attributes\" : { \"osm_id\":"
                            + osm_id
                            + ",\"building\":\"abandoned industrial complex\", \"way\" : \""
                            + StaticTestData.get("osm_polygon__geom_edit_28992")
                            + "\"}}"))
            .andExpect(status().is2xxSuccessful())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.__fid").value(__fid))
            .andExpect(jsonPath("$.attributes.building").value("abandoned industrial complex"))
            .andExpect(jsonPath("$.attributes.z_order").isEmpty())
            .andReturn();

    // check geometry equality
    final String body = result.getResponse().getContentAsString();
    final Geometry inputGeometry =
        GeometryProcessor.wktToGeometry(StaticTestData.get("osm_polygon__geom_edit_28992"));
    final Geometry geometry = GeometryProcessor.wktToGeometry(JsonPath.read(body, "$.geometry"));
    assertTrue(geometry.equalsExact(inputGeometry, 1.0), "Geometry is not equal");
    final Geometry way = GeometryProcessor.wktToGeometry(JsonPath.read(body, "$.attributes.way"));
    assertTrue(way.equalsExact(inputGeometry, 1.0), "Geometry is not equal");
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {ADMIN})
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void testPatchOrcl() throws Exception {
    final String url =
        apiBasePath + waterdeelUrlOracle + "/" + StaticTestData.get("waterdeel__fid_edit");
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
    final String url =
        apiBasePath + wegdeelUrlSqlserver + "/" + StaticTestData.get("wegdeel__fid_edit");
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
    final String url = apiBasePath + provinciesWFS + "/" + StaticTestData.get("utrecht__fid");
    mockMvc
        .perform(
            patch(url)
                .content("{\"attributes\":{\"naam\": \"Utereg\",\"code\":\"11\"}}")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(setServletPath(url)))
        .andExpect(status().is4xxClientError());
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
            + "/"
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
            + "/"
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
    final String url = apiBasePath + begroeidterreindeelUrlPostgis + "/" + "xxxxxx";
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
        apiBasePath + wegdeelUrlSqlserver + "/" + StaticTestData.get("wegdeel__fid_delete");
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
            + "/"
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
        apiBasePath + waterdeelUrlOracle + "/" + StaticTestData.get("waterdeel__fid_delete");
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
