/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.stream.Stream;
import nl.b3p.tailormap.api.annotation.PostgresIntegrationTest;
import nl.b3p.tailormap.api.viewer.model.Service;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junitpioneer.jupiter.DefaultTimeZone;
import org.junitpioneer.jupiter.Issue;
import org.junitpioneer.jupiter.Stopwatch;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.WKTReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@AutoConfigureMockMvc
@PostgresIntegrationTest
@Execution(ExecutionMode.CONCURRENT)
@Stopwatch
class FeaturesControllerPostgresIntegrationTest {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  /** bestuurlijke gebieden WFS; provincies . */
  private static final String provinciesWFS =
      "/app/default/layer/lyr:pdok-kadaster-bestuurlijkegebieden:Provinciegebied/features";

  private static final String begroeidterreindeelUrlPostgis =
      "/app/default/layer/lyr:snapshot-geoserver:postgis:begroeidterreindeel/features";
  private static final String waterdeelUrlOracle =
      "/app/default/layer/lyr:snapshot-geoserver:oracle:WATERDEEL/features";
  private static final String wegdeelUrlSqlserver =
      "/app/default/layer/lyr:snapshot-geoserver:sqlserver:wegdeel/features";
  /**
   * note that for WFS 2.0.0 this is -1 and for WFS 1.0.0 this is 12! depending on the value of
   * {@link #exactWfsCounts}.
   */
  private static final int provinciesWFSTotalCount = 12;

  private static final int begroeidterreindeelTotalCount = 3662;
  private static final int waterdeelTotalCount = 282;
  private static final int wegdeelTotalCount = 5934;

  @Value("${tailormap-api.base-path}")
  private String apiBasePath;

  @Autowired private MockMvc mockMvc;

  @Value("${tailormap-api.features.wfs_count_exact:false}")
  private boolean exactWfsCounts;

  @Value("${tailormap-api.pageSize}")
  private int pageSize;

  private static RequestPostProcessor requestPostProcessor(String servletPath) {
    return request -> {
      request.setServletPath(servletPath);
      return request;
    };
  }

  static Stream<Arguments> argumentsProvider() {
    return Stream.of(
        // docker host,table,url, feature count
        arguments(
            "postgis",
            "begroeidterreindeel",
            begroeidterreindeelUrlPostgis,
            begroeidterreindeelTotalCount),
        arguments("oracle", "waterdeel", waterdeelUrlOracle, waterdeelTotalCount),
        arguments("sqlserver", "wegdeel", wegdeelUrlSqlserver, wegdeelTotalCount));
  }

  static Stream<Arguments> differentFeatureSourcesProvider() {
    return Stream.of(
        arguments("wfs", provinciesWFS),
        arguments("postgis", begroeidterreindeelUrlPostgis),
        arguments("oracle", waterdeelUrlOracle),
        arguments("sqlserver", wegdeelUrlSqlserver));
  }

  static Stream<Arguments> projectionArgumentsProvider() {
    return Stream.of(
        // x, y, projection, distance,expected1stCoordinate, expected2ndCoordinate
        arguments(130794, 459169, "EPSG:28992", 5, 130873.9, 459308.9),
        arguments(52.12021, 5.03377, "EPSG:4326", /*~ 5 meter*/ 0.00005, 52.1, 5.0),
        arguments(560356, 6821890, "EPSG:3857", 5, 560485.3, 6822118.8));
  }

  static Stream<Arguments> wfsProjectionArgumentsProvider() {
    return Stream.of(
        // x, y, projection, distance,expected1stCoordinate, expected2ndCoordinate
        arguments(141247, 458118, "EPSG:28992", 5, 130179.9, 430066.3),
        arguments(52.11937, 5.04173, "EPSG:4326", /*~ 5 meter*/ 0.00005, 51.9, 5.2),
        arguments(577351, 6820242, "EPSG:3857", 5, 561478.9, 6774711.6));
  }

  /**
   * some test queries for the applayers (database/wfs featuretypes).
   *
   * @return a list of arguments for the test methods
   *     <p>see <a href="https://docs.geotools.org/latest/userguide/library/cql/ecql.html">CQL</a>
   */
  static Stream<Arguments> filtersProvider() {
    return Stream.of(
        // equals
        arguments(
            begroeidterreindeelUrlPostgis,
            "identificatie='L0002.5854010e82af4892986b8ec57bde6413'",
            1),
        arguments(waterdeelUrlOracle, "IDENTIFICATIE='W0636.729e31bc9e154f2c9fb72a9c733e7d64'", 1),
        arguments(wegdeelUrlSqlserver, "identificatie='G0344.9cbe9a54d127406087e76c102c6ddc45'", 1),
        arguments(provinciesWFS, "naam='Noord-Holland'", 1),
        arguments(provinciesWFS, "ligtInLandNaam='Nederland'", 12),
        // greater than
        arguments(begroeidterreindeelUrlPostgis, "relatievehoogteligging > 0", 2),
        arguments(waterdeelUrlOracle, "RELATIEVEHOOGTELIGGING > 0", 0),
        arguments(wegdeelUrlSqlserver, "relatievehoogteligging > 0", 152),
        // less than
        arguments(begroeidterreindeelUrlPostgis, "relatievehoogteligging < 0", 0),
        arguments(waterdeelUrlOracle, "RELATIEVEHOOGTELIGGING < 0", 1),
        arguments(wegdeelUrlSqlserver, "relatievehoogteligging < 0", 0),
        // equals or greater than
        arguments(
            begroeidterreindeelUrlPostgis,
            "relatievehoogteligging>=0",
            begroeidterreindeelTotalCount),
        arguments(waterdeelUrlOracle, "RELATIEVEHOOGTELIGGING>=0", waterdeelTotalCount - 1),
        arguments(wegdeelUrlSqlserver, "relatievehoogteligging>=0", wegdeelTotalCount),
        // equals or less than
        arguments(begroeidterreindeelUrlPostgis, "relatievehoogteligging<=0", 3660),
        arguments(waterdeelUrlOracle, "RELATIEVEHOOGTELIGGING<=0", waterdeelTotalCount),
        arguments(wegdeelUrlSqlserver, "relatievehoogteligging<=0", 5782),
        // in between
        arguments(begroeidterreindeelUrlPostgis, "relatievehoogteligging between -2 and 0", 3660),
        arguments(
            waterdeelUrlOracle, "RELATIEVEHOOGTELIGGING between -2 and 0", waterdeelTotalCount),
        arguments(wegdeelUrlSqlserver, "relatievehoogteligging between -2 and 0", 5782),
        // not in between / outside
        arguments(begroeidterreindeelUrlPostgis, "relatievehoogteligging not between -2 and 0", 2),
        arguments(waterdeelUrlOracle, "RELATIEVEHOOGTELIGGING not between -2 and 0", 0),
        arguments(wegdeelUrlSqlserver, "relatievehoogteligging not between -2 and 0", 152),
        // null value
        arguments(
            begroeidterreindeelUrlPostgis,
            "terminationdate is null",
            begroeidterreindeelTotalCount),
        arguments(waterdeelUrlOracle, "TERMINATIONDATE is null", waterdeelTotalCount),
        arguments(wegdeelUrlSqlserver, "terminationdate is null", wegdeelTotalCount),
        // not null value
        arguments(
            begroeidterreindeelUrlPostgis,
            "identificatie is not null",
            begroeidterreindeelTotalCount),
        arguments(waterdeelUrlOracle, "IDENTIFICATIE is not null", waterdeelTotalCount),
        arguments(wegdeelUrlSqlserver, "identificatie is not null", wegdeelTotalCount),
        // date equals w/ string argument (automatic conversion)
        arguments(begroeidterreindeelUrlPostgis, "creationdate='2016-04-18'", 980),
        arguments(waterdeelUrlOracle, "CREATIONDATE='2016-04-18'", 86),
        arguments(wegdeelUrlSqlserver, "creationdate='2016-04-18'", 2179),
        // date equals w/ TEQUALS function
        arguments(begroeidterreindeelUrlPostgis, "creationdate tequals 2016-04-18T00:00:00", 980),
        arguments(waterdeelUrlOracle, "CREATIONDATE tequals 2016-04-18T00:00:00", 86),
        arguments(wegdeelUrlSqlserver, "creationdate tequals 2016-04-18T00:00:00", 2179),
        // before date
        arguments(begroeidterreindeelUrlPostgis, "creationdate before 2016-04-18T00:00:00Z", 747),
        arguments(waterdeelUrlOracle, "CREATIONDATE before 2016-04-18T00:00:00Z", 71),
        arguments(wegdeelUrlSqlserver, "creationdate before 2016-04-18T00:00:00Z", 1178),
        // after date
        arguments(begroeidterreindeelUrlPostgis, "creationdate after 2016-04-18T00:00:00", 1935),
        arguments(waterdeelUrlOracle, "CREATIONDATE after 2016-04-18T00:00:00", 125),
        arguments(wegdeelUrlSqlserver, "creationdate after 2016-04-18T00:00:00", 2577),
        // between dates
        arguments(
            begroeidterreindeelUrlPostgis,
            "creationdate during 2016-04-18T00:00:00/2018-04-18T00:00:00",
            2217),
        arguments(
            waterdeelUrlOracle, "CREATIONDATE during 2016-04-18T00:00:00/2018-04-18T00:00:00", 157),
        arguments(
            wegdeelUrlSqlserver,
            "creationdate during 2016-04-18T00:00:00/2018-04-18T00:00:00",
            3864),
        // not between dates
        arguments(
            begroeidterreindeelUrlPostgis,
            "creationdate before 2016-04-18T00:00:00 or creationdate after 2018-04-18T00:00:00",
            1445),
        arguments(
            waterdeelUrlOracle,
            "CREATIONDATE before 2016-04-18T00:00:00 or CREATIONDATE after 2018-04-18T00:00:00",
            125),
        arguments(
            wegdeelUrlSqlserver,
            "creationdate before 2016-04-18T00:00:00 or creationdate after 2018-04-18T00:00:00",
            2070),
        // or w/ 3 expressions
        arguments(
            begroeidterreindeelUrlPostgis,
            // creationdate > '2016-04-18T00:00:00Z' or lv_publicatiedatum <
            // '2019-11-20T17:09:52Z' or lv_publicatiedatum > '2022-01-27T13:50:39Z'
            "creationdate after 2016-04-18T00:00:00Z or lv_publicatiedatum before 2019-11-20T17:09:52Z or lv_publicatiedatum after 2022-01-27T13:50:39Z",
            // Depends on timezone, this is for Europe/Amsterdam. For UTC 3522.
            3518),
        arguments(
            waterdeelUrlOracle,
            "CREATIONDATE after 2016-04-18T00:00:00Z or LV_PUBLICATIEDATUM before 2019-11-20T17:09:52Z or LV_PUBLICATIEDATUM after 2022-01-27T13:50:39Z",
            257),
        arguments(
            wegdeelUrlSqlserver,
            "creationdate after 2016-04-18T00:00:00Z or lv_publicatiedatum before 2019-11-20T17:09:52Z or lv_publicatiedatum after 2022-01-27T13:50:39Z",
            5591),
        // and w/ 2 expressions
        arguments(
            begroeidterreindeelUrlPostgis,
            // creationdate > '2016-04-18T00:00:00Z' and lv_publicatiedatum <
            // '2019-11-20T17:09:52Z'
            "creationdate after 2016-04-18T00:00:00Z and lv_publicatiedatum before 2019-11-20T17:09:52Z",
            // Depends on timezone, this is for Europe/Amsterdam. For UTC 1264.
            1271),
        arguments(
            waterdeelUrlOracle,
            "CREATIONDATE after 2016-04-18T00:00:00Z and LV_PUBLICATIEDATUM before 2019-11-20T17:09:52Z",
            77),
        arguments(
            wegdeelUrlSqlserver,
            // creationdate > '2016-04-18T00:00:00Z' and lv_publicatiedatum <
            // '2019-11-20T17:09:52Z'
            "creationdate after 2016-04-18T00:00:00Z and lv_publicatiedatum before 2019-11-20T17:09:52Z",
            // Depends on timezone, this is for Europe/Amsterdam. For UTC 1933.
            1963),
        // (not) like / ilike
        arguments(begroeidterreindeelUrlPostgis, "class like 'grasland%'", 85),
        arguments(
            begroeidterreindeelUrlPostgis,
            "class not like 'grasland%'",
            begroeidterreindeelTotalCount - 85),
        arguments(waterdeelUrlOracle, "CLASS like '%vlakte'", 16),
        arguments(wegdeelUrlSqlserver, "surfacematerial like '%verhard'", 106),
        arguments(provinciesWFS, "naam like '%-Holland'", 2),
        arguments(provinciesWFS, "naam ilike '%-holland'", 2));
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  @WithMockUser(
      username = "tm-admin",
      authorities = {"admin"})
  void broken_filter_not_supported() throws Exception {
    final String url = apiBasePath + provinciesWFS;
    mockMvc
        .perform(
            get(url)
                .accept(MediaType.APPLICATION_JSON)
                .with(requestPostProcessor(url))
                .param("filter", "naam or Utrecht")
                .param("page", "1"))
        .andExpect(status().is4xxClientError())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(400));
  }

  /**
   * requires layer "Provinciegebied" with id 2 and with wfs attributes to be configured, will fail
   * if configured postgres database is unavailable.
   *
   * @throws Exception if any
   */
  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  @WithMockUser(
      username = "tm-admin",
      authorities = {"admin"})
  void should_produce_for_valid_input_pdok_betuurlijkegebieden() throws Exception {
    final String url = apiBasePath + provinciesWFS;
    mockMvc
        .perform(
            get(url)
                .accept(MediaType.APPLICATION_JSON)
                .with(requestPostProcessor(url))
                .param("x", "141247")
                .param("y", "458118")
                .param("simplify", "true"))
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.features").isArray())
        .andExpect(jsonPath("$.features[0]").isMap())
        .andExpect(jsonPath("$.features[0]").isNotEmpty())
        .andExpect(jsonPath("$.features[0].__fid").isNotEmpty())
        .andExpect(jsonPath("$.features[0].geometry").isNotEmpty())
        .andExpect(jsonPath("$.features[0].attributes.naam").value("Utrecht"))
        .andExpect(jsonPath("$.features[0].attributes.ligtInLandNaam").value("Nederland"));
  }

  /* test EPSG:28992 (RD New/Amersfoort) */
  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  @WithMockUser(
      username = "tm-admin",
      authorities = {"admin"})
  void should_produce_for_valid_input_with_native_crs_pdok_betuurlijkegebieden() throws Exception {
    final String url = apiBasePath + provinciesWFS;
    mockMvc
        .perform(
            get(url)
                .accept(MediaType.APPLICATION_JSON)
                .with(requestPostProcessor(url))
                .param("x", "141247")
                .param("y", "458118")
                .param("crs", "EPSG:28992")
                .param("simplify", "true"))
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.features").isArray())
        .andExpect(jsonPath("$.features[0]").isMap())
        .andExpect(jsonPath("$.features[0]").isNotEmpty())
        .andExpect(jsonPath("$.features[0].__fid").isNotEmpty())
        .andExpect(jsonPath("$.features[0].geometry").isNotEmpty())
        .andExpect(jsonPath("$.features[0].attributes.naam").value("Utrecht"))
        .andExpect(jsonPath("$.features[0].attributes.ligtInLandNaam").value("Nederland"));
  }

  /* test EPSG:3857 (web mercator) */
  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  @WithMockUser(
      username = "tm-admin",
      authorities = {"admin"})
  void should_produce_for_valid_input_with_alien_crs_pdok_betuurlijkegebieden() throws Exception {
    final String url = apiBasePath + provinciesWFS;
    mockMvc
        .perform(
            get(url)
                .accept(MediaType.APPLICATION_JSON)
                .with(requestPostProcessor(url))
                .param("x", "577351")
                .param("y", "6820242")
                .param("crs", "EPSG:3857")
                .param("simplify", "true"))
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.features").isArray())
        .andExpect(jsonPath("$.features[0]").isMap())
        .andExpect(jsonPath("$.features[0]").isNotEmpty())
        .andExpect(jsonPath("$.features[0].__fid").isNotEmpty())
        .andExpect(jsonPath("$.features[0].geometry").isNotEmpty())
        .andExpect(jsonPath("$.features[0].attributes.naam").value("Utrecht"))
        .andExpect(jsonPath("$.features[0].attributes.ligtInLandNaam").value("Nederland"));
  }

  /* test EPSG:4326 (WGS84) */
  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  @WithMockUser(
      username = "tm-admin",
      authorities = {"admin"})
  void should_produce_for_valid_input_with_alien_crs_2_pdok_betuurlijkegebieden() throws Exception {
    final String url = apiBasePath + provinciesWFS;
    mockMvc
        .perform(
            get(url)
                .accept(MediaType.APPLICATION_JSON)
                .with(requestPostProcessor(url))
                // note flipped axis
                .param("y", "5.04173")
                .param("x", "52.11937")
                .param("crs", "EPSG:4326")
                .param("simplify", "true")
                .param("distance", /*~ 4 meter*/ "0.00004"))
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.features").isArray())
        .andExpect(jsonPath("$.features[0]").isMap())
        .andExpect(jsonPath("$.features[0]").isNotEmpty())
        .andExpect(jsonPath("$.features[0].__fid").isNotEmpty())
        .andExpect(jsonPath("$.features[0].geometry").isNotEmpty())
        .andExpect(jsonPath("$.features[0].attributes.naam").value("Utrecht"))
        .andExpect(jsonPath("$.features[0].attributes.ligtInLandNaam").value("Nederland"));
  }

  /**
   * request 2 pages data from the bestuurlijke gebieden WFS featuretype provincies.
   *
   * @throws Exception if any
   */
  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {"admin"})
  void should_return_non_empty_featurecollections_for_valid_page_from_wfs() throws Exception {
    // bestuurlijke gebieden WFS; provincies
    // page 1
    final String url = apiBasePath + provinciesWFS;
    MvcResult result =
        mockMvc
            .perform(
                get(url)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(requestPostProcessor(url))
                    .param("page", "1"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.total").value(exactWfsCounts ? provinciesWFSTotalCount : -1))
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.pageSize").value(pageSize))
            .andExpect(jsonPath("$.features").isArray())
            .andExpect(jsonPath("$.features").isNotEmpty())
            .andExpect(jsonPath("$.features[0]").isMap())
            .andExpect(jsonPath("$.features[0]").isNotEmpty())
            .andExpect(jsonPath("$.features[0].__fid").isNotEmpty())
            .andExpect(jsonPath("$.features[0].geometry").isEmpty())
            .andExpect(jsonPath("$.features[0].attributes.naam").value("Zuid-Holland"))
            .andExpect(jsonPath("$.features[0].attributes.ligtInLandNaam").value("Nederland"))
            .andExpect(jsonPath("$.columnMetadata").isArray())
            .andExpect(jsonPath("$.columnMetadata").isNotEmpty())
            .andReturn();

    String body = result.getResponse().getContentAsString();
    logger.trace(body);
    assertNotNull(body, "response body should not be null");
    List<Service> page1Features = JsonPath.read(body, "$.features");
    assertEquals(
        pageSize,
        page1Features.size(),
        () ->
            "there should be "
                + pageSize
                + " provinces in the list, but was "
                + page1Features.size());

    // page 2
    result =
        // bestuurlijke gebieden WFS; provincies
        mockMvc
            .perform(
                get(url)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(requestPostProcessor(url))
                    .param("page", "2"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.total").value(exactWfsCounts ? provinciesWFSTotalCount : -1))
            .andExpect(jsonPath("$.page").value(2))
            .andExpect(jsonPath("$.pageSize").value(pageSize))
            .andExpect(jsonPath("$.features").isArray())
            .andExpect(jsonPath("$.features").isNotEmpty())
            .andExpect(jsonPath("$.columnMetadata").isArray())
            .andExpect(jsonPath("$.columnMetadata").isNotEmpty())
            .andReturn();

    body = result.getResponse().getContentAsString();
    logger.trace(body);
    assertNotNull(body, "response body should not be null");

    List<Service> page2Features = JsonPath.read(body, "$.features");
    assertEquals(
        2,
        page2Features.size(),
        () -> "there should be 2 provinces in the list, but was " + page2Features.size());

    // check for duplicates
    int retrievedFeatsCount = page1Features.size() + page2Features.size();
    page2Features.addAll(page1Features);
    assertEquals(
        retrievedFeatsCount,
        (int) page2Features.stream().distinct().count(),
        "there should be no duplicates in 2 sequential pages");
  }

  /**
   * request an out-of-range page of data from the bestuurlijke gebieden WFS featuretype provincies.
   *
   * @throws Exception if any
   */
  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {"admin"})
  void should_return_empty_featurecollection_for_out_of_range_page_from_wfs() throws Exception {
    // bestuurlijke gebieden WFS; provincies
    // page 3
    final String url = apiBasePath + provinciesWFS;
    MvcResult result =
        mockMvc
            .perform(
                get(url)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(requestPostProcessor(url))
                    .param("page", "3"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.total").value(exactWfsCounts ? provinciesWFSTotalCount : -1))
            .andExpect(jsonPath("$.page").value(3))
            .andExpect(jsonPath("$.pageSize").value(pageSize))
            .andExpect(jsonPath("$.features").isArray())
            .andExpect(jsonPath("$.features").isEmpty())
            .andReturn();
    String body = result.getResponse().getContentAsString();
    logger.trace(body);
    assertNotNull(body, "response body should not be null");
    List<Service> features = JsonPath.read(body, "$.features");
    assertEquals(0, features.size(), "there should be 0 provinces in the list");
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  @WithMockUser(
      username = "tm-admin",
      authorities = {"admin"})
  void should_return_default_sorted_featurecollections_for_no_or_invalid_sorting_from_wfs()
      throws Exception {
    // page 1, sort by naam, no direction
    final String url = apiBasePath + provinciesWFS;
    mockMvc
        .perform(
            get(url)
                .accept(MediaType.APPLICATION_JSON)
                .with(requestPostProcessor(url))
                .param("page", "1")
                .param("sortBy", "naam"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.total").value(exactWfsCounts ? provinciesWFSTotalCount : -1))
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.pageSize").value(pageSize))
        .andExpect(jsonPath("$.features").isArray())
        .andExpect(jsonPath("$.features").isNotEmpty())
        .andExpect(jsonPath("$.features[0]").isMap())
        .andExpect(jsonPath("$.features[0]").isNotEmpty())
        .andExpect(jsonPath("$.features[0].__fid").isNotEmpty())
        .andExpect(jsonPath("$.features[0].geometry").isEmpty())
        .andExpect(jsonPath("$.features[0].attributes.naam").value("Drenthe"))
        .andExpect(jsonPath("$.features[0].attributes.ligtInLandNaam").value("Nederland"))
        .andExpect(jsonPath("$.features[9]").isMap())
        .andExpect(jsonPath("$.features[9]").isNotEmpty())
        .andExpect(jsonPath("$.features[9].__fid").isNotEmpty())
        .andExpect(jsonPath("$.features[9].geometry").isEmpty())
        .andExpect(jsonPath("$.features[9].attributes.naam").value("Utrecht"))
        .andExpect(jsonPath("$.features[9].attributes.ligtInLandNaam").value("Nederland"))
        .andExpect(jsonPath("$.columnMetadata").isArray())
        .andExpect(jsonPath("$.columnMetadata").isNotEmpty());

    // page 1, sort by naam, invalid direction
    mockMvc
        .perform(
            get(url)
                .accept(MediaType.APPLICATION_JSON)
                .with(requestPostProcessor(url))
                .param("page", "1")
                .param("sortBy", "naam")
                .param("sortOrder", "invalid"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.total").value(exactWfsCounts ? provinciesWFSTotalCount : -1))
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.pageSize").value(pageSize))
        .andExpect(jsonPath("$.features").isArray())
        .andExpect(jsonPath("$.features").isNotEmpty())
        .andExpect(jsonPath("$.features[0]").isMap())
        .andExpect(jsonPath("$.features[0]").isNotEmpty())
        .andExpect(jsonPath("$.features[0].__fid").isNotEmpty())
        .andExpect(jsonPath("$.features[0].geometry").isEmpty())
        .andExpect(jsonPath("$.features[0].attributes.naam").value("Drenthe"))
        .andExpect(jsonPath("$.features[0].attributes.ligtInLandNaam").value("Nederland"))
        .andExpect(jsonPath("$.features[9]").isMap())
        .andExpect(jsonPath("$.features[9]").isNotEmpty())
        .andExpect(jsonPath("$.features[9].__fid").isNotEmpty())
        .andExpect(jsonPath("$.features[9].geometry").isEmpty())
        .andExpect(jsonPath("$.features[9].attributes.naam").value("Utrecht"))
        .andExpect(jsonPath("$.features[9].attributes.ligtInLandNaam").value("Nederland"))
        .andExpect(jsonPath("$.columnMetadata").isArray())
        .andExpect(jsonPath("$.columnMetadata").isNotEmpty());
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  @WithMockUser(
      username = "tm-admin",
      authorities = {"admin"})
  void should_return_sorted_featurecollections_for_valid_sorting_from_wfs() throws Exception {
    // bestuurlijke gebieden WFS; provincies
    // page 1, sort ascending by naam
    final String url = apiBasePath + provinciesWFS;
    mockMvc
        .perform(
            get(url)
                .accept(MediaType.APPLICATION_JSON)
                .with(requestPostProcessor(url))
                .param("page", "1")
                .param("sortBy", "naam")
                .param("sortOrder", "asc"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.total").value(exactWfsCounts ? provinciesWFSTotalCount : -1))
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.pageSize").value(pageSize))
        .andExpect(jsonPath("$.features").isArray())
        .andExpect(jsonPath("$.features").isNotEmpty())
        .andExpect(jsonPath("$.features[0]").isMap())
        .andExpect(jsonPath("$.features[0]").isNotEmpty())
        .andExpect(jsonPath("$.features[0].__fid").isNotEmpty())
        .andExpect(jsonPath("$.features[0].geometry").isEmpty())
        .andExpect(jsonPath("$.features[0].attributes.naam").value("Drenthe"))
        .andExpect(jsonPath("$.features[0].attributes.ligtInLandNaam").value("Nederland"))
        .andExpect(jsonPath("$.features[9]").isMap())
        .andExpect(jsonPath("$.features[9]").isNotEmpty())
        .andExpect(jsonPath("$.features[9].__fid").isNotEmpty())
        .andExpect(jsonPath("$.features[9].geometry").isEmpty())
        .andExpect(jsonPath("$.features[9].attributes.naam").value("Utrecht"))
        .andExpect(jsonPath("$.features[9].attributes.ligtInLandNaam").value("Nederland"))
        .andExpect(jsonPath("$.columnMetadata").isArray())
        .andExpect(jsonPath("$.columnMetadata").isNotEmpty());

    // page 1, sort descending by naam
    mockMvc
        .perform(
            get(url)
                .accept(MediaType.APPLICATION_JSON)
                .with(requestPostProcessor(url))
                .param("page", "1")
                .param("sortBy", "naam")
                .param("sortOrder", "desc"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.total").value(exactWfsCounts ? provinciesWFSTotalCount : -1))
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.pageSize").value(pageSize))
        .andExpect(jsonPath("$.features").isArray())
        .andExpect(jsonPath("$.features").isNotEmpty())
        .andExpect(jsonPath("$.features[0]").isMap())
        .andExpect(jsonPath("$.features[0]").isNotEmpty())
        .andExpect(jsonPath("$.features[0].__fid").isNotEmpty())
        .andExpect(jsonPath("$.features[0].geometry").isEmpty())
        .andExpect(jsonPath("$.features[0].attributes.naam").value("Zuid-Holland"))
        .andExpect(jsonPath("$.features[0].attributes.ligtInLandNaam").value("Nederland"))
        .andExpect(jsonPath("$.features[8]").isMap())
        .andExpect(jsonPath("$.features[8]").isNotEmpty())
        .andExpect(jsonPath("$.features[8].__fid").isNotEmpty())
        .andExpect(jsonPath("$.features[8].geometry").isEmpty())
        .andExpect(jsonPath("$.features[8].attributes.naam").value("Gelderland"))
        .andExpect(jsonPath("$.features[8].attributes.ligtInLandNaam").value("Nederland"))
        .andExpect(jsonPath("$.columnMetadata").isArray())
        .andExpect(jsonPath("$.columnMetadata").isNotEmpty());
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  @WithMockUser(
      username = "tm-admin",
      authorities = {"admin"})
  void should_return_sorted_featurecollections_for_valid_sorting_from_database() throws Exception {
    // begroeidterreindeel from postgis
    final String url = apiBasePath + begroeidterreindeelUrlPostgis;
    // page 1, sort ascending by gmlid
    mockMvc
        .perform(
            get(url)
                .with(requestPostProcessor(url))
                .accept(MediaType.APPLICATION_JSON)
                .param("page", "1")
                .param("sortBy", "gmlid")
                .param("sortOrder", "asc"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.total").value(begroeidterreindeelTotalCount))
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.pageSize").value(pageSize))
        .andExpect(jsonPath("$.features").isArray())
        .andExpect(jsonPath("$.features").isNotEmpty())
        .andExpect(jsonPath("$.features[0]").isMap())
        .andExpect(jsonPath("$.features[0]").isNotEmpty())
        .andExpect(jsonPath("$.features[0].__fid").isNotEmpty())
        .andExpect(
            jsonPath("$.features[0].__fid")
                .value("begroeidterreindeel.000f22d5ea3eace21bd39111a7212bd9"));

    // page 1, sort descending by gmlid
    mockMvc
        .perform(
            get(url)
                .with(requestPostProcessor(url))
                .accept(MediaType.APPLICATION_JSON)
                .param("page", "1")
                .param("sortBy", "gmlid")
                .param("sortOrder", "desc"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.total").value(begroeidterreindeelTotalCount))
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.pageSize").value(pageSize))
        .andExpect(jsonPath("$.features").isArray())
        .andExpect(jsonPath("$.features").isNotEmpty())
        .andExpect(jsonPath("$.features[0]").isMap())
        .andExpect(jsonPath("$.features[0]").isNotEmpty())
        .andExpect(jsonPath("$.features[0].__fid").isNotEmpty())
        .andExpect(
            jsonPath("$.features[0].__fid")
                .value("begroeidterreindeel.fff17bee0b9f3c51db387a0ecd364457"));
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  @WithMockUser(
      username = "tm-admin",
      authorities = {"admin"})
  void get_by_fid_from_database() throws Exception {
    final String url = apiBasePath + begroeidterreindeelUrlPostgis;
    mockMvc
        .perform(
            get(url)
                .with(requestPostProcessor(url))
                .param("__fid", "begroeidterreindeel.fff17bee0b9f3c51db387a0ecd364457")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.features").isArray())
        .andExpect(jsonPath("$.features").isNotEmpty())
        .andExpect(jsonPath("$.features[0]").isMap())
        .andExpect(jsonPath("$.features[0]").isNotEmpty())
        .andExpect(jsonPath("$.features[0].__fid").isNotEmpty())
        .andExpect(jsonPath("$.features[0].geometry").isNotEmpty())
        .andExpect(
            jsonPath("$.features[0].__fid")
                .value("begroeidterreindeel.fff17bee0b9f3c51db387a0ecd364457"));
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  @WithMockUser(
      username = "tm-admin",
      authorities = {"admin"})
  void get_by_fid_from_wfs() throws Exception {
    // note that this test may break when pdok decides to opdate the data or the service.
    // you can get the fid by clicking on the Utrecht feature in the map.
    // alternatively this test could be written to use the wfs service to first get Utrecht
    // feature by naam and then do the fid test.
    final String utrecht__fid = "Provinciegebied.209e5db1-05cc-4201-9ff6-02f60c51b880";
    final String url = apiBasePath + provinciesWFS;

    mockMvc
        .perform(
            get(url)
                .with(requestPostProcessor(url))
                .param("__fid", utrecht__fid)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.features").isArray())
        .andExpect(jsonPath("$.features").isNotEmpty())
        .andExpect(jsonPath("$.features[0]").isMap())
        .andExpect(jsonPath("$.features[0]").isNotEmpty())
        .andExpect(jsonPath("$.features[0].__fid").isNotEmpty())
        .andExpect(jsonPath("$.features[0].geometry").isNotEmpty())
        .andExpect(jsonPath("$.features[0].attributes.naam").value("Utrecht"))
        .andExpect(jsonPath("$.features[0].__fid").value(utrecht__fid));
  }

  /**
   * request 2 pages of data from a database featuretype.
   *
   * @throws Exception if any
   */
  @ParameterizedTest(
      name =
          "#{index}: should return non-empty featurecollections for valid page from database: {0}, featuretype: {1}")
  @MethodSource("argumentsProvider")
  @WithMockUser(
      username = "tm-admin",
      authorities = {"admin"})
  void should_return_non_empty_featurecollections_for_valid_pages_from_database(
      String ignoredDatabase, String ignoredTableName, String applayerUrl, int totalCcount)
      throws Exception {
    applayerUrl = apiBasePath + applayerUrl;
    // page 1
    MvcResult result =
        mockMvc
            .perform(
                get(applayerUrl)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(requestPostProcessor(applayerUrl))
                    .param("page", "1"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.total").value(totalCcount))
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.pageSize").value(pageSize))
            .andExpect(jsonPath("$.features").isArray())
            .andExpect(jsonPath("$.features").isNotEmpty())
            .andExpect(jsonPath("$.features[0]").isMap())
            .andExpect(jsonPath("$.features[0]").isNotEmpty())
            .andExpect(jsonPath("$.features[0].__fid").isNotEmpty())
            .andExpect(jsonPath("$.features[0].geometry").isEmpty())
            .andExpect(jsonPath("$.columnMetadata").isArray())
            .andExpect(jsonPath("$.columnMetadata").isNotEmpty())
            .andReturn();

    String body = result.getResponse().getContentAsString();
    logger.trace(body);
    assertNotNull(body, "response body should not be null");
    List<Service> page1Features = JsonPath.read(body, "$.features");
    assertEquals(
        pageSize,
        page1Features.size(),
        () ->
            "there should be "
                + pageSize
                + " features in the list, but was "
                + page1Features.size());

    // page 2
    result =
        mockMvc
            .perform(
                get(applayerUrl)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(requestPostProcessor(applayerUrl))
                    .param("page", "2"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.total").value(totalCcount))
            .andExpect(jsonPath("$.page").value(2))
            .andExpect(jsonPath("$.pageSize").value(pageSize))
            .andExpect(jsonPath("$.features").isArray())
            .andExpect(jsonPath("$.features").isNotEmpty())
            .andExpect(jsonPath("$.features[0]").isMap())
            .andExpect(jsonPath("$.features[0]").isNotEmpty())
            .andExpect(jsonPath("$.features[0].__fid").isNotEmpty())
            .andExpect(jsonPath("$.features[0].geometry").isEmpty())
            .andExpect(jsonPath("$.columnMetadata").isArray())
            .andExpect(jsonPath("$.columnMetadata").isNotEmpty())
            .andReturn();

    body = result.getResponse().getContentAsString();
    logger.trace(body);
    assertNotNull(body, "response body should not be null");
    List<Service> page2Features = JsonPath.read(body, "$.features");
    assertEquals(
        pageSize,
        page2Features.size(),
        () ->
            "there should be "
                + pageSize
                + " features in the list, but was "
                + page2Features.size());

    // check for duplicates
    page2Features.addAll(page1Features);
    assertEquals(
        2 * pageSize,
        (int) page2Features.stream().distinct().count(),
        "there should be no duplicates in 2 sequential pages");
  }

  @ParameterizedTest(
      name =
          "should return expected polygon feature for valid coordinates and crs from database #{index}: x: {0}, y: {1}, crs: {2}")
  @MethodSource("projectionArgumentsProvider")
  @WithMockUser(
      username = "tm-admin",
      authorities = {"admin"})
  void should_produce_reprojected_features_from_database_using_different_crs(
      double x,
      double y,
      String crs,
      double distance,
      double expected1stCoordinate,
      double expected2ndCoordinate)
      throws Exception {
    final String expectedFid = "begroeidterreindeel.3fdcbafb5c4c1d7481e916ae5200fcc4";
    final String url = apiBasePath + begroeidterreindeelUrlPostgis;

    MvcResult result =
        mockMvc
            .perform(
                get(url)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(requestPostProcessor(url))
                    .param("x", String.valueOf(x))
                    .param("y", String.valueOf(y))
                    .param("crs", crs)
                    .param("distance", String.valueOf(distance))
                    .param("simplify", "true")
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.features").isArray())
            .andExpect(jsonPath("$.features").isNotEmpty())
            .andExpect(jsonPath("$.features[0]").isMap())
            .andExpect(jsonPath("$.features[0]").isNotEmpty())
            .andExpect(jsonPath("$.features[0].__fid").isNotEmpty())
            .andExpect(jsonPath("$.features[0].__fid").value(expectedFid))
            .andExpect(jsonPath("$.features[0].geometry").isNotEmpty())
            .andReturn();
    String body = result.getResponse().getContentAsString();
    String geometry = JsonPath.read(body, "$.features[0].geometry");
    Geometry g = new WKTReader().read(geometry);
    assertEquals(Polygon.class, g.getClass(), "Did not find expected geometry type");
    assertEquals(
        expected1stCoordinate,
        g.getCoordinate().getX(),
        .1,
        "x coordinate should be " + expected1stCoordinate);
    assertEquals(
        expected2ndCoordinate,
        g.getCoordinate().getY(),
        .1,
        "y coordinate should be " + expected2ndCoordinate);
  }

  @ParameterizedTest(
      name =
          "should return expected polygon feature for valid coordinates and crs from WFS #{index}: x: {0}, y: {1}, crs: {2}")
  @MethodSource("wfsProjectionArgumentsProvider")
  @WithMockUser(
      username = "tm-admin",
      authorities = {"admin"})
  void should_produce_reprojected_features_from_wfs_using_different_crs(
      double x,
      double y,
      String crs,
      double distance,
      double expected1stCoordinate,
      double expected2ndCoordinate)
      throws Exception {

    final String expectedFid = "Provinciegebied.209e5db1-05cc-4201-9ff6-02f60c51b880";
    final String url = apiBasePath + provinciesWFS;

    MvcResult result =
        mockMvc
            .perform(
                get(url)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(requestPostProcessor(url))
                    .param("x", String.valueOf(x))
                    .param("y", String.valueOf(y))
                    .param("crs", crs)
                    .param("distance", String.valueOf(distance))
                    .param("simplify", "true")
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.features").isArray())
            .andExpect(jsonPath("$.features").isNotEmpty())
            .andExpect(jsonPath("$.features[0]").isMap())
            .andExpect(jsonPath("$.features[0]").isNotEmpty())
            .andExpect(jsonPath("$.features[0].__fid").isNotEmpty())
            .andExpect(jsonPath("$.features[0].__fid").value(expectedFid))
            .andExpect(jsonPath("$.features[0].geometry").isNotEmpty())
            .andExpect(jsonPath("$.features[0].attributes.naam").value("Utrecht"))
            .andExpect(jsonPath("$.features[0].attributes.ligtInLandNaam").value("Nederland"))
            .andReturn();

    String body = result.getResponse().getContentAsString();
    String geometry = JsonPath.read(body, "$.features[0].geometry");
    Geometry g = new WKTReader().read(geometry);
    assertEquals(Polygon.class, g.getClass(), "Did not find expected geometry type");
    assertEquals(
        expected1stCoordinate,
        g.getCoordinate().getX(),
        .1,
        "x coordinate should be " + expected1stCoordinate);
    assertEquals(
        expected2ndCoordinate,
        g.getCoordinate().getY(),
        .1,
        "y coordinate should be " + expected2ndCoordinate);
  }

  /**
   * request the same page of data from a database featuretype twice and compare.
   *
   * @throws Exception if any
   */
  @ParameterizedTest(
      name =
          "#{index}: should return same featurecollection for same page from database: {0}, featuretype: {1}")
  @MethodSource("argumentsProvider")
  @WithMockUser(
      username = "tm-admin",
      authorities = {"admin"})
  void should_return_same_featurecollection_for_same_page_database(
      String ignoredDatabase, String ignoredTableName, String applayerUrl, int totalCcount)
      throws Exception {
    applayerUrl = apiBasePath + applayerUrl;

    // page 1
    MvcResult result =
        mockMvc
            .perform(
                get(applayerUrl)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(requestPostProcessor(applayerUrl))
                    .param("page", "1"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.total").value(totalCcount))
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.pageSize").value(pageSize))
            .andExpect(jsonPath("$.features").isArray())
            .andExpect(jsonPath("$.features").isNotEmpty())
            .andExpect(jsonPath("$.features[0]").isMap())
            .andExpect(jsonPath("$.features[0]").isNotEmpty())
            .andExpect(jsonPath("$.features[0].__fid").isNotEmpty())
            .andExpect(jsonPath("$.features[0].geometry").isEmpty())
            .andExpect(jsonPath("$.columnMetadata").isArray())
            .andExpect(jsonPath("$.columnMetadata").isNotEmpty())
            .andReturn();

    String body = result.getResponse().getContentAsString();
    logger.trace(body);
    assertNotNull(body, "response body should not be null");
    List<Service> page1Features = JsonPath.read(body, "$.features");

    // page 1 again
    result =
        mockMvc
            .perform(
                get(applayerUrl)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(requestPostProcessor(applayerUrl))
                    .param("page", "1"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.total").value(totalCcount))
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.pageSize").value(pageSize))
            .andExpect(jsonPath("$.features").isArray())
            .andExpect(jsonPath("$.features").isNotEmpty())
            .andExpect(jsonPath("$.features[0]").isMap())
            .andExpect(jsonPath("$.features[0]").isNotEmpty())
            .andExpect(jsonPath("$.features[0].__fid").isNotEmpty())
            .andExpect(jsonPath("$.features[0].geometry").isEmpty())
            .andExpect(jsonPath("$.columnMetadata").isArray())
            .andExpect(jsonPath("$.columnMetadata").isNotEmpty())
            .andReturn();

    body = result.getResponse().getContentAsString();
    logger.trace(body);
    assertNotNull(body, "response body should not be null");
    List<Service> page2Features = JsonPath.read(body, "$.features");

    assertEquals(
        page1Features,
        page2Features,
        "2 identical page requests should give two identical lists of features");
  }

  /**
   * request an out-of-range page of data from a database featuretype.
   *
   * @throws Exception if any
   */
  @ParameterizedTest(
      name =
          "#{index}: should return empty featurecollection for out of range page from database: {0}, featuretype: {1}")
  @MethodSource("argumentsProvider")
  @WithMockUser(
      username = "tm-admin",
      authorities = {"admin"})
  void should_return_empty_featurecollection_for_out_of_range_page_database(
      String ignoredDatabase, String ignoredTableName, String applayerUrl, int totalCcount)
      throws Exception {
    applayerUrl = apiBasePath + applayerUrl;
    // request page ...
    int page = (totalCcount / pageSize) + 5;
    MvcResult result =
        mockMvc
            .perform(
                get(applayerUrl)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(requestPostProcessor(applayerUrl))
                    .param("page", String.valueOf(page)))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.total").value(totalCcount))
            .andExpect(jsonPath("$.page").value(page))
            .andExpect(jsonPath("$.pageSize").value(pageSize))
            .andExpect(jsonPath("$.features").isArray())
            .andExpect(jsonPath("$.features").isEmpty())
            .andReturn();
    String body = result.getResponse().getContentAsString();
    logger.trace(body);
    assertNotNull(body, "response body should not be null");
    List<Service> features = JsonPath.read(body, "$.features");
    assertEquals(0, features.size(), "there should be 0 features in the list");
  }

  @ParameterizedTest(
      name =
          "#{index} should return a featurecollection for various ECQL filters on appLayer: {0}, filter: {1}")
  @MethodSource("filtersProvider")
  @WithMockUser(
      username = "tm-admin",
      authorities = {"admin"})
  @DefaultTimeZone("Europe/Amsterdam")
  void filterTest(String appLayerUrl, String filterCQL, int totalCount) throws Exception {
    int listSize = Math.min(pageSize, totalCount);
    if (!exactWfsCounts && appLayerUrl.equals(provinciesWFS)) {
      // see #extractWfsCount and property 'tailormap-api.features.wfs_count_exact'
      totalCount = -1;
    }

    appLayerUrl = apiBasePath + appLayerUrl;

    MvcResult result =
        mockMvc
            .perform(
                get(appLayerUrl)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(requestPostProcessor(appLayerUrl))
                    .param("filter", filterCQL)
                    .param("page", "1"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.pageSize").value(pageSize))
            .andExpect(jsonPath("$.total").value(totalCount))
            .andExpect(jsonPath("$.features").isArray())
            .andReturn();
    final String body = result.getResponse().getContentAsString();
    logger.trace(body);
    assertNotNull(body, "response body should not be null");
    final List<Service> features = JsonPath.read(body, "$.features");

    assertEquals(
        listSize, features.size(), () -> "there should be " + listSize + " features in the list");
  }

  @ParameterizedTest(name = "#{index} should return onlyGeometries for {0}, appLayer: {1}")
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  @MethodSource("differentFeatureSourcesProvider")
  void onlyGeometries(@SuppressWarnings("unused") String source, String appLayerUrl)
      throws Exception {

    appLayerUrl = apiBasePath + appLayerUrl;

    mockMvc
        .perform(
            get(appLayerUrl)
                .accept(MediaType.APPLICATION_JSON)
                .with(requestPostProcessor(appLayerUrl))
                .param("onlyGeometries", "true")
                .param("page", "1"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.features").isArray())
        .andExpect(jsonPath("$.features[0].geometry").isNotEmpty());
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  @WithMockUser(
      username = "tm-admin",
      authorities = {"admin"})
  void only_filter_not_supported() throws Exception {
    final String url = apiBasePath + provinciesWFS;
    mockMvc
        .perform(
            get(url)
                .accept(MediaType.APPLICATION_JSON)
                .with(requestPostProcessor(url))
                .param("filter", "naam=Utrecht"))
        .andExpect(status().is4xxClientError())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(400))
        .andExpect(jsonPath("$.message").value("Unsupported combination of request parameters"));
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  @WithMockUser(
      username = "tm-admin",
      authorities = {"admin"})
  void givenOnly_XorY_shouldError() throws Exception {
    final String url = apiBasePath + provinciesWFS;
    mockMvc
        .perform(
            get(url)
                .accept(MediaType.APPLICATION_JSON)
                .with(requestPostProcessor(url))
                .param("x", "3"))
        .andExpect(status().is4xxClientError())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(400));

    mockMvc
        .perform(
            get(url)
                .accept(MediaType.APPLICATION_JSON)
                .with(requestPostProcessor(url))
                .param("y", "3"))
        .andExpect(status().is4xxClientError())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(400));
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  @WithMockUser(
      username = "tm-admin",
      authorities = {"admin"})
  void given_distance_NotGreaterThanZero() throws Exception {
    final String url = apiBasePath + provinciesWFS;
    mockMvc
        .perform(
            get(url)
                .accept(MediaType.APPLICATION_JSON)
                .with(requestPostProcessor(url))
                .param("x", "3")
                .param("y", "3")
                .param("distance", "0"))
        .andExpect(status().is4xxClientError())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(400))
        .andExpect(jsonPath("$.message").value("Buffer distance must be greater than 0"));

    mockMvc
        .perform(
            get(url)
                .accept(MediaType.APPLICATION_JSON)
                .with(requestPostProcessor(url))
                .param("x", "3")
                .param("y", "3")
                .param("distance", "-1"))
        .andExpect(status().is4xxClientError())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(400))
        .andExpect(jsonPath("$.message").value("Buffer distance must be greater than 0"));
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  @WithMockUser(
      username = "tm-admin",
      authorities = {"admin"})
  void should_error_when_calling_with_nonexistent_appId() throws Exception {
    final String url = apiBasePath + "/app/400/layer/1/features";
    mockMvc
        .perform(get(url).accept(MediaType.APPLICATION_JSON).with(requestPostProcessor(url)))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(404));
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  @WithMockUser(
      username = "tm-admin",
      authorities = {"admin"})
  void should_not_find_when_called_without_appId() throws Exception {
    final String url = apiBasePath + "/app/layer/features";
    mockMvc
        .perform(get(url).accept(MediaType.APPLICATION_JSON).with(requestPostProcessor(url)))
        .andExpect(status().isNotFound());
  }

  @Test
  @Order(Integer.MAX_VALUE)
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void should_send_403_when_access_denied() throws Exception {
    final String url =
        apiBasePath + "/app/secured/layer/lyr:pdok-kadaster-bestuurlijkegebieden:Provinciegebied/features";
    mockMvc
        .perform(
            get(url)
                .accept(MediaType.APPLICATION_JSON)
                .with(requestPostProcessor(url))
                .param("page", "1"))
        .andExpect(status().isForbidden())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(403))
        .andExpect(jsonPath("$.message").value("Access denied"));
  }
}
