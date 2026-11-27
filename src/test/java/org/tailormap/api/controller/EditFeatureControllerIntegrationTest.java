/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller;

import static java.util.Map.entry;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.tailormap.api.IntegrationTestOrdering.EDIT_FEATURES_CONTROLLER_INTEGRATION_TEST_ORDER;
import static org.tailormap.api.TestJson.json;
import static org.tailormap.api.TestRequestProcessor.setServletPath;
import static org.tailormap.api.persistence.Group.ADMIN;

import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.junitpioneer.jupiter.Stopwatch;
import org.locationtech.jts.geom.Geometry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.tailormap.api.SpatialTestDataRestorer;
import org.tailormap.api.StaticTestData;
import org.tailormap.api.annotation.PostgresIntegrationTest;
import org.tailormap.api.geotools.processing.GeometryProcessor;
import org.tailormap.api.persistence.TMFeatureType;
import org.tailormap.api.repository.FeatureSourceRepository;
import org.tailormap.api.repository.FeatureTypeRepository;

@PostgresIntegrationTest
@AutoConfigureMockMvc
@Stopwatch
@TestClassOrder(ClassOrderer.OrderAnnotation.class)
@Order(EDIT_FEATURES_CONTROLLER_INTEGRATION_TEST_ORDER)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(value = ExecutionMode.SAME_THREAD, reason = "we need specific ordering of tests")
// Inserts and deletes features in the spatial test databases, so it must not run concurrently with tests that
// count or export those features
@Isolated
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

  /** Primary keys of the features inserted by the tests, to delete them afterwards. */
  private static final String pgInsertedGmlid = "aaa"
      + StaticTestData.get("begroeidterreindeel__fid_edit")
          .substring(
              StaticTestData.get("begroeidterreindeel__fid_edit").indexOf('.') + 4);

  private static final String mssqlInsertedGmlid = "b3p"
      + StaticTestData.get("wegdeel__fid_edit")
          .substring(StaticTestData.get("wegdeel__fid_edit").indexOf('.') + 4);

  private static final String oracleInsertedGmlid = "b3p"
      + StaticTestData.get("waterdeel__fid_edit")
          .substring(StaticTestData.get("waterdeel__fid_edit").indexOf('.') + 4);

  private static final String osmInsertedFid = StaticTestData.get("osm_polygon__fid_edit") + "1234";

  @Value("${tailormap-api.base-path}")
  private String apiBasePath;

  @Autowired
  private FeatureSourceRepository featureSourceRepository;

  @Autowired
  private FeatureTypeRepository featureTypeRepository;

  private final SpatialTestDataRestorer spatialTestDataRestorer = new SpatialTestDataRestorer();

  @Autowired
  private WebApplicationContext context;

  private MockMvc mockMvc;

  @BeforeAll
  void initialize() throws IOException {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

    // Save the features the tests modify or delete and remove the features the tests insert afterwards, so the test
    // data in the spatial databases is unchanged after the tests
    final TMFeatureType begroeidterreindeel = featureType("PostGIS", "begroeidterreindeel");
    spatialTestDataRestorer.save(
        begroeidterreindeel,
        StaticTestData.get("begroeidterreindeel__fid_edit"),
        StaticTestData.get("begroeidterreindeel__fid_delete"));
    spatialTestDataRestorer.removeOnRestore(begroeidterreindeel, "begroeidterreindeel." + pgInsertedGmlid);

    final TMFeatureType wegdeel = featureType("MS SQL Server", "wegdeel");
    spatialTestDataRestorer.save(
        wegdeel, StaticTestData.get("wegdeel__fid_edit"), StaticTestData.get("wegdeel__fid_delete"));
    spatialTestDataRestorer.removeOnRestore(wegdeel, "wegdeel." + mssqlInsertedGmlid);

    final TMFeatureType waterdeel = featureType("Oracle", "WATERDEEL");
    spatialTestDataRestorer.save(
        waterdeel, StaticTestData.get("waterdeel__fid_edit"), StaticTestData.get("waterdeel__fid_delete"));
    spatialTestDataRestorer.removeOnRestore(waterdeel, "WATERDEEL." + oracleInsertedGmlid);

    final TMFeatureType osmPolygon = featureType("PostGIS OSM", "osm_polygon");
    spatialTestDataRestorer.save(osmPolygon, StaticTestData.get("osm_polygon__fid_edit"));
    spatialTestDataRestorer.removeOnRestore(osmPolygon, osmInsertedFid);
  }

  @AfterAll
  void restoreSpatialTestData() throws IOException {
    spatialTestDataRestorer.restore();
  }

  private TMFeatureType featureType(String featureSourceTitle, String featureTypeName) {
    return featureTypeRepository
        .getTMFeatureTypeByNameAndFeatureSource(
            featureTypeName,
            featureSourceRepository.getByTitle(featureSourceTitle).orElseThrow())
        .orElseThrow();
  }

  @Test
  void unauthenticated_delete() throws Exception {
    final String url = apiBasePath
        + begroeidterreindeelUrlPostgis
        + "/"
        + StaticTestData.get("begroeidterreindeel__fid_delete");
    mockMvc.perform(delete(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(401))
        .andExpect(jsonPath("$.url").value("/login"));
  }

  @Test
  void unauthenticated_post() throws Exception {
    final String url = apiBasePath + begroeidterreindeelUrlPostgis;
    // A mutable map, because Map.of() does not allow null values
    final Map<String, Object> body = new HashMap<>();
    body.put("geom", null);
    body.put("attributes", Map.of("case", "irrelevant"));

    mockMvc.perform(post(url)
            .accept(MediaType.APPLICATION_JSON)
            .with(setServletPath(url))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(body)))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(401))
        .andExpect(jsonPath("$.url").value("/login"));
  }

  @Test
  void unauthenticated_patch() throws Exception {
    final String url =
        apiBasePath + begroeidterreindeelUrlPostgis + "/" + StaticTestData.get("begroeidterreindeel__fid_edit");
    mockMvc.perform(patch(url)
            .accept(MediaType.APPLICATION_JSON)
            .with(setServletPath(url))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("properties", Map.of("naam", "test")))))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(401))
        .andExpect(jsonPath("$.url").value("/login"));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {ADMIN})
  void non_editable_patch() throws Exception {
    final String url = apiBasePath
        + begroeidterreindeelUrlPostgisNonEditable
        + "/"
        + StaticTestData.get("begroeidterreindeel__fid_edit");
    mockMvc.perform(patch(url)
            .accept(MediaType.APPLICATION_JSON)
            .with(setServletPath(url))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of(
                "__fid",
                StaticTestData.get("begroeidterreindeel__fid_edit"),
                "attributes",
                Map.of(
                    "class",
                    "weggemaaid grasland",
                    "geom",
                    StaticTestData.get("begroeidterreindeel__geom_edit"))))))
        .andExpect(status().is4xxClientError())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value("400"))
        .andExpect(jsonPath("$.message").value("Layer is not editable"));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {ADMIN})
  void patch_non_existent_attribute() throws Exception {
    final String url =
        apiBasePath + begroeidterreindeelUrlPostgis + "/" + StaticTestData.get("begroeidterreindeel__fid_edit");
    mockMvc.perform(patch(url)
            .accept(MediaType.APPLICATION_JSON)
            .with(setServletPath(url))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("attributes", Map.of("doesnotexist", true)))))
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
  void patch_hidden_attribute() throws Exception {
    final String url =
        apiBasePath + begroeidterreindeelUrlPostgis + "/" + StaticTestData.get("begroeidterreindeel__fid_edit");

    final String body = json(
        Map.of("attributes", Map.of("terminationdate", "something", "geom_kruinlijn", "LINESTRING(0 0, 1 1)")));

    mockMvc.perform(patch(url)
            .accept(MediaType.APPLICATION_JSON)
            .with(setServletPath(url))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
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
  void patch_hidden_attribute_in_app_layer_settings() throws Exception {
    final String url =
        apiBasePath + begroeidterreindeelUrlPostgis + "/" + StaticTestData.get("begroeidterreindeel__fid_edit");

    final String body = json(Map.of("attributes", Map.of("begroeidterreindeeloptalud", "something")));

    mockMvc.perform(patch(url)
            .accept(MediaType.APPLICATION_JSON)
            .with(setServletPath(url))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
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
  void patch_read_only_attribute() throws Exception {
    final String url =
        apiBasePath + begroeidterreindeelUrlPostgis + "/" + StaticTestData.get("begroeidterreindeel__fid_edit");

    final String body = json(Map.of("attributes", Map.of("eindregistratie", "something")));

    mockMvc.perform(patch(url)
            .accept(MediaType.APPLICATION_JSON)
            .with(setServletPath(url))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
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
  void patch_primary_key_attribute() throws Exception {
    final String url =
        apiBasePath + begroeidterreindeelUrlPostgis + "/" + StaticTestData.get("begroeidterreindeel__fid_edit");

    final String body = json(Map.of("attributes", Map.of("gmlid", "something")));

    mockMvc.perform(patch(url)
            .accept(MediaType.APPLICATION_JSON)
            .with(setServletPath(url))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().is4xxClientError())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(400))
        .andExpect(jsonPath("$.message")
            .value("Feature cannot be edited, the primary key attribute cannot be changed"));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {ADMIN})
  @Order(1)
  void post_pg_feature() throws Exception {
    final String url = apiBasePath + begroeidterreindeelUrlPostgis;
    final String gmlid = pgInsertedGmlid;
    final String fid = "begroeidterreindeel." + gmlid;
    final String content = json(Map.of(
        "__fid",
        fid,
        "attributes",
        Map.ofEntries(
            entry("gmlid", gmlid),
            entry("identificatie", "B3P." + gmlid),
            entry("lv_publicatiedatum", "2021-01-15T10:33:08.000+00:00"),
            entry("creationdate", "2020-12-23"),
            entry("tijdstipregistratie", "2021-01-15T07:00:12.000+00:00"),
            entry("bronhouder", "B3P"),
            entry("inonderzoek", true),
            entry("relatievehoogteligging", 0),
            entry("bgt_status", "bestaand"),
            entry("plus_status", "geenWaarde"),
            entry("plus_fysiekvoorkomen", "waardeOnbekend"),
            entry("class", "weggemaaid grasland"),
            entry("geom", StaticTestData.get("begroeidterreindeel__geom_edit")))));

    mockMvc.perform(post(url)
            .accept(MediaType.APPLICATION_JSON)
            .with(setServletPath(url))
            .contentType(MediaType.APPLICATION_JSON)
            .content(content))
        .andExpect(status().is2xxSuccessful())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.__fid").value(fid))
        .andExpect(jsonPath("$.geometry").value(StaticTestData.get("begroeidterreindeel__geom_edit")))
        .andExpect(jsonPath("$.attributes.inonderzoek").value(true))
        .andExpect(jsonPath("$.attributes.geom").value(StaticTestData.get("begroeidterreindeel__geom_edit")))
        .andExpect(jsonPath("$.attributes.geom_kruinlijn").isEmpty())
        .andExpect(jsonPath("$.attributes.class").value("weggemaaid grasland"))
        // a date column is serialized as a date without a time (HTM-2287)
        .andExpect(jsonPath("$.attributes.creationdate").value("2020-12-23"))
        .andExpect(jsonPath("$.attachments").isEmpty());
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {ADMIN})
  @Order(10)
  void post_duplicate_primary_key_pg() throws Exception {
    final String url = apiBasePath + begroeidterreindeelUrlPostgis;
    final String gmlid = pgInsertedGmlid;
    final String fid = "begroeidterreindeel." + gmlid;
    final String content = json(Map.of(
        "__fid",
        fid,
        "attributes",
        Map.ofEntries(
            entry("gmlid", gmlid),
            entry("identificatie", "B3P." + gmlid),
            entry("tijdstipregistratie", "2021-01-15T07:00:12.000+00:00"),
            entry("bronhouder", "B3P"),
            entry("inonderzoek", true),
            entry("relatievehoogteligging", 0),
            entry("bgt_status", "bestaand"),
            entry("plus_status", "geenWaarde"),
            entry("plus_fysiekvoorkomen", "waardeOnbekend"),
            entry("class", "weggemaaid grasland"),
            entry("geom", StaticTestData.get("begroeidterreindeel__geom_edit")))));

    mockMvc.perform(post(url)
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
  void post_mssql_feature() throws Exception {
    final String url = apiBasePath + wegdeelUrlSqlserver;
    final String gmlid = mssqlInsertedGmlid;
    final String fid = "wegdeel." + gmlid;
    // A mutable map, because Map.of() does not allow the null value for geom_kruinlijn
    final Map<String, Object> attributes = new HashMap<>(Map.ofEntries(
        entry("gmlid", gmlid),
        entry("identificatie", "B3P." + gmlid),
        entry("plus_functiewegdeel", "waardeOnbekend"),
        entry("creationdate", "2016-12-06"),
        entry("plus_status", "geenWaarde"),
        entry("bronhouder", "B3P"),
        entry("surfacematerial", "gesloten verharding"),
        entry("plus_fysiekvoorkomenwegdeel", "waardeOnbekend"),
        entry("geom", StaticTestData.get("wegdeel__geom_edit")),
        entry("bgt_status", "bestaand"),
        entry("lv_publicatiedatum", "2019-11-15T20:18:09.000+00:00"),
        entry("relatievehoogteligging", 0),
        entry("function_", "rijbaan autosnelweg"),
        entry("wegdeeloptalud", false),
        entry("inonderzoek", true),
        entry("tijdstipregistratie", "2019-11-15T12:52:08.000+00:00")));
    attributes.put("geom_kruinlijn", null);
    final String content = json(Map.of("__fid", fid, "attributes", attributes));

    mockMvc.perform(post(url)
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
  void post_oracle_feature() throws Exception {
    final String url = apiBasePath + waterdeelUrlOracle;
    final String gmlid = oracleInsertedGmlid;
    final String fid = "WATERDEEL." + gmlid;
    final String content = json(Map.of(
        "__fid",
        fid,
        "attributes",
        Map.ofEntries(
            entry("GMLID", gmlid),
            entry("IDENTIFICATIE", "B3P." + gmlid),
            entry("CREATIONDATE", "2016-12-06"),
            entry("PLUS_STATUS", "geenWaarde"),
            entry("BRONHOUDER", "B3P"),
            entry("CLASS", "waterloop"),
            entry("PLUS_TYPE", "waardeOnbekend"),
            entry("GEOM", StaticTestData.get("waterdeel__edit_geom")),
            entry("BGT_STATUS", "bestaand"),
            entry("LV_PUBLICATIEDATUM", "2019-11-15T20:18:09.000+00:00"),
            entry("RELATIEVEHOOGTELIGGING", 0),
            entry("INONDERZOEK", true),
            entry("TIJDSTIPREGISTRATIE", "2019-11-15T12:52:08.000+00:00"))));

    mockMvc.perform(post(url)
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
  void patch_invalid_wkt_should_not_clear_geometry() throws Exception {
    final String url =
        apiBasePath + begroeidterreindeelUrlPostgis + "/" + StaticTestData.get("begroeidterreindeel__fid_edit");
    mockMvc.perform(patch(url)
            .accept(MediaType.APPLICATION_JSON)
            .with(setServletPath(url))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of(
                "__fid",
                StaticTestData.get("begroeidterreindeel__fid_edit"),
                "attributes",
                Map.of("geom", "POLYGON((1 2, 3 4"))))) // missing closing parentheses
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.message").value(containsString("Invalid WKT for geometry attribute \"geom\"")));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {ADMIN})
  void patch_geometry_that_is_not_a_string() throws Exception {
    final String url =
        apiBasePath + begroeidterreindeelUrlPostgis + "/" + StaticTestData.get("begroeidterreindeel__fid_edit");
    mockMvc.perform(patch(url)
            .accept(MediaType.APPLICATION_JSON)
            .with(setServletPath(url))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of(
                "__fid",
                StaticTestData.get("begroeidterreindeel__fid_edit"),
                "attributes",
                Map.of("geom", 42)))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Value of geometry attribute \"geom\" must be WKT"));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {ADMIN})
  void patch_non_existent_feature() throws Exception {
    final String url = apiBasePath + begroeidterreindeelUrlPostgis + "/begroeidterreindeel.does-not-exist";
    mockMvc.perform(patch(url)
            .accept(MediaType.APPLICATION_JSON)
            .with(setServletPath(url))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of(
                "__fid",
                "begroeidterreindeel.does-not-exist",
                "attributes",
                Map.of("inonderzoek", true)))))
        .andExpect(status().isNotFound())
        .andExpect(
            jsonPath("$.message").value("Feature with fid begroeidterreindeel.does-not-exist not found"));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {ADMIN})
  void patch_pg_feature() throws Exception {
    final String url =
        apiBasePath + begroeidterreindeelUrlPostgis + "/" + StaticTestData.get("begroeidterreindeel__fid_edit");
    mockMvc.perform(patch(url)
            .accept(MediaType.APPLICATION_JSON)
            .with(setServletPath(url))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of(
                "__fid",
                StaticTestData.get("begroeidterreindeel__fid_edit"),
                "attributes",
                Map.of(
                    "inonderzoek",
                    true,
                    "class",
                    "weggemaaid grasland",
                    "geom",
                    StaticTestData.get("begroeidterreindeel__geom_edit"))))))
        .andExpect(status().is2xxSuccessful())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.__fid").value(StaticTestData.get("begroeidterreindeel__fid_edit")))
        .andExpect(jsonPath("$.geometry").value(StaticTestData.get("begroeidterreindeel__geom_edit")))
        .andExpect(jsonPath("$.attributes.inonderzoek").value(true))
        .andExpect(jsonPath("$.attributes.geom").value(StaticTestData.get("begroeidterreindeel__geom_edit")))
        .andExpect(jsonPath("$.attributes.geom_kruinlijn").isEmpty())
        .andExpect(jsonPath("$.attributes.class").value("weggemaaid grasland"))
        // the attachment is added in
        // org.tailormap.api.controller.AttachmentsControllerIntegrationTest#addAttachment()
        .andExpect(jsonPath("$.attachments").isNotEmpty())
        .andExpect(jsonPath("$.attachments").isArray())
        .andExpect(jsonPath("$.attachments[0].attributeName").value("bijlage"))
        .andExpect(jsonPath("$.attachments[0].description").value("A test SVG attachment"));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {ADMIN})
  void patch_foreign_crs_pg_feature() throws Exception {
    final String url = apiBasePath + osm_polygonUrlPostgis + "/" + StaticTestData.get("osm_polygon__fid_edit");
    final MvcResult result = mockMvc.perform(patch(url)
            .accept(MediaType.APPLICATION_JSON)
            .with(setServletPath(url))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of(
                "__fid",
                StaticTestData.get("osm_polygon__fid_edit"),
                "attributes",
                Map.of(
                    "building",
                    "abandoned industrial complex",
                    "way",
                    StaticTestData.get("osm_polygon__geom_edit_28992"))))))
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
  void post_foreign_crs_pg_feature() throws Exception {
    final String __fid = osmInsertedFid;
    final String osm_id = __fid.replace("osm_polygon.", "");
    final String url = apiBasePath + osm_polygonUrlPostgis;
    final String content = json(Map.of(
        "__fid",
        __fid,
        "attributes",
        Map.of(
            "osm_id",
            Long.parseLong(osm_id),
            "building",
            "abandoned industrial complex",
            "way",
            StaticTestData.get("osm_polygon__geom_edit_28992"))));

    final MvcResult result = mockMvc.perform(post(url)
            .accept(MediaType.APPLICATION_JSON)
            .with(setServletPath(url))
            .contentType(MediaType.APPLICATION_JSON)
            .content(content))
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
  void patch_oracle_feature() throws Exception {
    final String url = apiBasePath + waterdeelUrlOracle + "/" + StaticTestData.get("waterdeel__fid_edit");
    mockMvc.perform(patch(url)
            .accept(MediaType.APPLICATION_JSON)
            .with(setServletPath(url))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of(
                "attributes",
                Map.of(
                    "INONDERZOEK",
                    true,
                    "CLASS",
                    "woeste bergbeek",
                    "GEOM",
                    StaticTestData.get("waterdeel__edit_geom"))))))
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
  void patch_mssql_feature() throws Exception {
    final String url = apiBasePath + wegdeelUrlSqlserver + "/" + StaticTestData.get("wegdeel__fid_edit");
    mockMvc.perform(patch(url)
            .accept(MediaType.APPLICATION_JSON)
            .with(setServletPath(url))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of(
                "attributes",
                Map.of(
                    "inonderzoek",
                    true,
                    "surfacematerial",
                    "weggemaaid grasland",
                    "geom",
                    StaticTestData.get("wegdeel__geom_edit"))))))
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
  void patch_wfs_feature() throws Exception {
    final String url = apiBasePath + provinciesWFS + "/" + StaticTestData.get("utrecht__fid");
    mockMvc.perform(patch(url)
            .content(json(Map.of("attributes", Map.of("naam", "Utereg", "code", "11"))))
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .with(setServletPath(url)))
        .andExpect(status().is4xxClientError());
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {ADMIN})
  void get_is_unsupported() throws Exception {
    final String url =
        apiBasePath + begroeidterreindeelUrlPostgis + "/" + StaticTestData.get("begroeidterreindeel__fid_edit");
    mockMvc.perform(get(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
        .andExpect(status().is4xxClientError())
        .andExpect(status().is(405));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {ADMIN})
  void put_is_unsupported() throws Exception {
    final String url =
        apiBasePath + begroeidterreindeelUrlPostgis + "/" + StaticTestData.get("begroeidterreindeel__fid_edit");
    mockMvc.perform(put(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
        .andExpect(status().is4xxClientError())
        .andExpect(status().is(405));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {ADMIN})
  void delete_when_layer_does_not_exist() throws Exception {
    final String url = apiBasePath + "/app/default/layer/lyr:doesnotexist:doesnotexist/edit/feature/does.not.1";
    mockMvc.perform(delete(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
        .andExpect(status().is4xxClientError())
        .andExpect(status().is(404));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {ADMIN})
  void delete_non_existent_feature() throws Exception {
    final String url = apiBasePath + begroeidterreindeelUrlPostgis + "/" + "xxxxxx";
    // geotools does not report back that the feature does not exist, nor the number of deleted
    // features, no error === success
    mockMvc.perform(delete(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
        .andExpect(status().is2xxSuccessful())
        .andExpect(status().is(204));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {ADMIN})
  void delete_existing_feature_mssql() throws Exception {
    final String url = apiBasePath + wegdeelUrlSqlserver + "/" + StaticTestData.get("wegdeel__fid_delete");
    mockMvc.perform(delete(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
        .andExpect(status().is2xxSuccessful())
        .andExpect(status().is(204));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {ADMIN})
  void delete_existing_feature_pg() throws Exception {
    final String url = apiBasePath
        + begroeidterreindeelUrlPostgis
        + "/"
        + StaticTestData.get("begroeidterreindeel__fid_delete");
    mockMvc.perform(delete(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
        .andExpect(status().is2xxSuccessful())
        .andExpect(status().is(204));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {ADMIN})
  void delete_existing_feature_oracle() throws Exception {
    final String url = apiBasePath + waterdeelUrlOracle + "/" + StaticTestData.get("waterdeel__fid_delete");
    mockMvc.perform(delete(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
        .andExpect(status().is2xxSuccessful())
        .andExpect(status().is(204));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {ADMIN})
  void delete_existing_feature_wfs() throws Exception {
    final String url = apiBasePath + provinciesWFS + StaticTestData.get("utrecht__fid");
    mockMvc.perform(delete(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
        .andExpect(status().is4xxClientError())
        .andExpect(status().is(400));
  }
}
