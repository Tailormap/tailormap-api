/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller;

import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.tailormap.api.IntegrationTestOrdering.FIRST_INTEGRATION_TEST_ORDER;
import static org.tailormap.api.TestRequestProcessor.setServletPath;
import static org.tailormap.api.controller.TestUrls.layerBegroeidTerreindeelPostgis;
import static org.tailormap.api.controller.TestUrls.layerOsmPolygonPostgis;
import static org.tailormap.api.controller.TestUrls.layerProvinciesWfs;
import static org.tailormap.api.controller.TestUrls.layerWaterdeelOracle;
import static org.tailormap.api.controller.TestUrls.layerWegdeelSqlServer;

import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junitpioneer.jupiter.Stopwatch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.tailormap.api.annotation.PostgresIntegrationTest;

@AutoConfigureMockMvc
@PostgresIntegrationTest
@Execution(ExecutionMode.CONCURRENT)
@Stopwatch
@Order(FIRST_INTEGRATION_TEST_ORDER)
class LayerBoundsControllerIntegrationTest {
  private static final String controllerPath = "/bounds";
  private static final String provinciesWfs = layerProvinciesWfs + controllerPath;
  private static final String osm_polygonUrlPostgis = layerOsmPolygonPostgis + controllerPath;
  private static final String begroeidterreindeelUrlPostgis = layerBegroeidTerreindeelPostgis + controllerPath;
  private static final String waterdeelUrlOracle = layerWaterdeelOracle + controllerPath;
  private static final String wegdeelUrlSqlserver = layerWegdeelSqlServer + controllerPath;

  record Box(double minx, double miny, double maxx, double maxy) {}

  static Stream<Arguments> differentFeatureSourcesProvider() {
    return Stream.of(
        arguments(
            provinciesWfs,
            new Box(-49153.75688053126, 246980.21778224222, 296833.6569084727, 863630.9095935024)),
        arguments(
            osm_polygonUrlPostgis,
            // EPSG:3857: BOX(551613.0569744045 6810595.19371569,574504.7970611334 6855047.1016635075)
            // new Box(125373.57282795662, 452261.857003541, 139561.88517970705, 479440.38233562664)),
            // it seems geotools reprojection is off in Y direction by about 60m? but close enough...
            new Box(125373.572828, 452197.95276, 139561.885180, 479504.05102)),
        arguments(begroeidterreindeelUrlPostgis, new Box(129764.216, 457608.821, 134324.346, 460269.264)),
        arguments(waterdeelUrlOracle, new Box(128886.8, 457314.107, 134465.788, 461349.894)),
        arguments(wegdeelUrlSqlserver, new Box(129475.237, 457855.977, 134326.007, 462129.323)));
  }

  /**
   * some test queries for the applayers (database/wfs featuretypes).
   *
   * @return a list of arguments for the test methods
   *     <p>see <a href="https://docs.geotools.org/latest/userguide/library/cql/ecql.html">CQL</a>
   */
  static Stream<Arguments> filtersProvider() {
    return Stream.of(
        // single features
        arguments(
            begroeidterreindeelUrlPostgis,
            "identificatie='L0002.5854010e82af4892986b8ec57bde6413'",
            new Box(131996.028, 458074.715, 132085.093, 458147.064)),
        arguments(
            waterdeelUrlOracle,
            "IDENTIFICATIE='W0636.729e31bc9e154f2c9fb72a9c733e7d64'",
            new Box(129921.371, 457915.51, 130053.441, 458231.584)),
        arguments(
            wegdeelUrlSqlserver,
            "identificatie='G0344.9cbe9a54d127406087e76c102c6ddc45'",
            new Box(133444.884, 459023.914, 133467.407, 459046.522)),
        arguments(provinciesWfs, "code='26'", new Box(114240.997, 429919.006, 171506.371, 479588.289)),

        // combination of multiple filters, including spatial filter (intersects with buffer of polygon)
        arguments(
            begroeidterreindeelUrlPostgis,
            "((((creationdate BEFORE 2025-06-05T00:00:00.000Z) AND "
                + "(plus_fysiekvoorkomen IN "
                + "('bodembedekkers','bosplantsoen','gras- en kruidachtigen','griend en hakhout')"
                + ")) AND"
                + " INTERSECTS(geom, BUFFER(POLYGON((130333.71 459898.38,130012.78 459669.77,130264.84"
                + " 459250.21,130787.91 459290.39,130751.85 459731.07,130333.71 459898.38)), 10))) AND"
                + " (bronhouder IN ('G1904')))",
            new Box(130015.847, 459147.467, 130997.996, 460030.68)),

        // (not) like / ilike
        arguments(
            begroeidterreindeelUrlPostgis,
            "class like 'grasland%'",
            new Box(130003.456, 457949.226, 134132.211, 460220.59)),
        arguments(
            begroeidterreindeelUrlPostgis,
            "class not like 'grasland%'",
            new Box(129764.216, 457608.821, 134324.346, 460269.264)),
        arguments(provinciesWfs, "naam ilike '%-holland'", new Box(43662.62, 406692.0, 154328.67, 581000.0)));
  }

  static Stream<Arguments> nonFulfillingFiltersProvider() {
    return Stream.of(
        // no features that fulfill the filter, bad requests
        arguments(begroeidterreindeelUrlPostgis, "identificatie='non-existent'"),
        arguments(waterdeelUrlOracle, "IDENTIFICATIE='non-existent'"),
        arguments(wegdeelUrlSqlserver, "identificatie='non-existent'"),
        arguments(provinciesWfs, "code='non-existent'"));
  }

  private static final double DEFAULT_ALLOWED_ERROR = 0.1;
  private static final double OSM_ALLOWED_ERROR = 1.0;

  @Value("${tailormap-api.base-path}")
  private String apiBasePath;

  @Autowired
  private MockMvc mockMvc;

  @ParameterizedTest(name = "#{index} get should return valid envelope for appLayer: {0}")
  @MethodSource("differentFeatureSourcesProvider")
  void get_unfiltered_bounds(String appLayerUrl, Box bounds) throws Exception {
    double allowedError =
        Objects.equals(appLayerUrl, osm_polygonUrlPostgis) ? OSM_ALLOWED_ERROR : DEFAULT_ALLOWED_ERROR;

    appLayerUrl = apiBasePath + appLayerUrl;
    mockMvc.perform(get(appLayerUrl).accept(MediaType.APPLICATION_JSON).with(setServletPath(appLayerUrl)))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$").isMap())
        .andExpect(jsonPath("$.maxx").isNumber())
        .andExpect(jsonPath("$.maxx")
            .value(is(both(greaterThan(bounds.minx)).and(is(lessThanOrEqualTo(bounds.maxx))))))
        .andExpect(jsonPath("$.maxy").isNumber())
        .andExpect(jsonPath("$.maxy").value(is(closeTo(bounds.maxy, allowedError))))
        .andExpect(jsonPath("$.minx").isNumber())
        .andExpect(jsonPath("$.minx").value(is(closeTo(bounds.minx, allowedError))))
        .andExpect(jsonPath("$.miny").isNumber())
        .andExpect(jsonPath("$.miny").value(is(closeTo(bounds.miny, allowedError))));
  }

  @ParameterizedTest(name = "#{index} post should return valid envelope for appLayer: {0}")
  @MethodSource("differentFeatureSourcesProvider")
  void post_unfiltered_bounds(String appLayerUrl, Box bounds) throws Exception {
    double allowedError =
        Objects.equals(appLayerUrl, osm_polygonUrlPostgis) ? OSM_ALLOWED_ERROR : DEFAULT_ALLOWED_ERROR;

    appLayerUrl = apiBasePath + appLayerUrl;
    mockMvc.perform(post(appLayerUrl)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .accept(MediaType.APPLICATION_JSON)
            .with(setServletPath(appLayerUrl))
            .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$").isMap())
        .andExpect(jsonPath("$.maxx").isNumber())
        .andExpect(jsonPath("$.maxx")
            .value(is(both(greaterThan(bounds.minx)).and(is(lessThanOrEqualTo(bounds.maxx))))))
        .andExpect(jsonPath("$.maxy").isNumber())
        .andExpect(jsonPath("$.maxy").value(is(closeTo(bounds.maxy, allowedError))))
        .andExpect(jsonPath("$.minx").isNumber())
        .andExpect(jsonPath("$.minx").value(is(closeTo(bounds.minx, allowedError))))
        .andExpect(jsonPath("$.miny").isNumber())
        .andExpect(jsonPath("$.miny").value(is(closeTo(bounds.miny, allowedError))));
  }

  @ParameterizedTest(name = "#{index} get should return valid envelope for appLayer: {0} and filter {1}")
  @MethodSource("filtersProvider")
  void get_filtered_bounds(String appLayerUrl, String cqlFilter, Box bounds) throws Exception {
    double allowedError =
        Objects.equals(appLayerUrl, osm_polygonUrlPostgis) ? OSM_ALLOWED_ERROR : DEFAULT_ALLOWED_ERROR;

    appLayerUrl = apiBasePath + appLayerUrl;
    mockMvc.perform(get(appLayerUrl)
            .param("filter", cqlFilter)
            .accept(MediaType.APPLICATION_JSON)
            .with(setServletPath(appLayerUrl)))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$").isMap())
        .andExpect(jsonPath("$.maxx").isNumber())
        .andExpect(jsonPath("$.maxx")
            .value(is(both(greaterThan(bounds.minx)).and(is(lessThanOrEqualTo(bounds.maxx))))))
        .andExpect(jsonPath("$.maxy").isNumber())
        .andExpect(jsonPath("$.maxy").value(is(closeTo(bounds.maxy, allowedError))))
        .andExpect(jsonPath("$.minx").isNumber())
        .andExpect(jsonPath("$.minx").value(is(closeTo(bounds.minx, allowedError))))
        .andExpect(jsonPath("$.miny").isNumber())
        .andExpect(jsonPath("$.miny").value(is(closeTo(bounds.miny, allowedError))));
  }

  @ParameterizedTest(name = "#{index} post should return Bad Request for appLayer: {0} and (unfulfilling) filter {1}")
  @MethodSource("nonFulfillingFiltersProvider")
  void post_unfulfilling_filter(String appLayerUrl, String cqlFilter) throws Exception {
    appLayerUrl = apiBasePath + appLayerUrl;
    mockMvc.perform(post(appLayerUrl)
            .param("filter", cqlFilter)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .accept(MediaType.APPLICATION_JSON)
            .with(setServletPath(appLayerUrl))
            .with(csrf()))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON));
  }

  @Test
  void invalid_filter_produces_bad_request() throws Exception {
    String appLayerUrl = apiBasePath + begroeidterreindeelUrlPostgis;
    mockMvc.perform(get(appLayerUrl)
            .param("filter", "not a valid filter")
            .accept(MediaType.APPLICATION_JSON)
            .with(setServletPath(appLayerUrl)))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON));
  }

  @Test
  void invalid_layer_cannot_be_found() throws Exception {
    String appLayerUrl = apiBasePath + "/app/default/layer/does-not-exist" + controllerPath;
    mockMvc.perform(get(appLayerUrl).accept(MediaType.APPLICATION_JSON).with(setServletPath(appLayerUrl)))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON));
  }
}
