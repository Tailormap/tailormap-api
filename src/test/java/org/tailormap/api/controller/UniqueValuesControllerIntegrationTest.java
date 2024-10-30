/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.tailormap.api.TestRequestProcessor.setServletPath;
import static org.tailormap.api.controller.TestUrls.layerBegroeidTerreindeelPostgis;
import static org.tailormap.api.controller.TestUrls.layerProvinciesWfs;
import static org.tailormap.api.controller.TestUrls.layerProxiedWithAuthInPublicApp;
import static org.tailormap.api.controller.TestUrls.layerWaterdeelOracle;
import static org.tailormap.api.controller.TestUrls.layerWegdeelSqlServer;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junitpioneer.jupiter.Issue;
import org.junitpioneer.jupiter.RetryingTest;
import org.junitpioneer.jupiter.Stopwatch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.tailormap.api.annotation.PostgresIntegrationTest;

@PostgresIntegrationTest
@AutoConfigureMockMvc
@Execution(ExecutionMode.CONCURRENT)
@Stopwatch
@Order(1)
class UniqueValuesControllerIntegrationTest {
  @Value("${tailormap-api.base-path}")
  private String apiBasePath;

  private static final String provinciesWfsUrl = layerProvinciesWfs + "/unique/naam";
  private static final String begroeidterreindeelPostgisUrl =
      layerBegroeidTerreindeelPostgis + "/unique/bronhouder";
  private static final String waterdeelOracleUrl = layerWaterdeelOracle + "/unique/BRONHOUDER";
  private static final String wegdeelSqlServerUrl = layerWegdeelSqlServer + "/unique/bronhouder";

  @Autowired private MockMvc mockMvc;

  /** layer url + bronhouders. */
  static Stream<Arguments> databaseArgumentsProvider() {
    return Stream.of(
        arguments(
            begroeidterreindeelPostgisUrl,
            new String[] {"W0636", "G0344", "L0004", "W0155", "L0001", "P0026", "L0002", "G1904"}),
        arguments(
            waterdeelOracleUrl,
            new String[] {"W0636", "P0026", "L0002", "W0155", "G1904", "G0344", "L0004"}),
        arguments(wegdeelSqlServerUrl, new String[] {"P0026", "G0344", "G1904", "L0004", "L0002"}));
  }

  @ParameterizedTest(name = "#{index}: should return all unique values from database: {0}")
  @MethodSource("databaseArgumentsProvider")
  void bronhouder_unique_values_test(String url, String... expected) throws Exception {
    url = apiBasePath + url;
    MvcResult result =
        mockMvc
            .perform(get(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.filterApplied").value(false))
            .andExpect(jsonPath("$.values").isArray())
            .andExpect(jsonPath("$.values").isNotEmpty())
            .andReturn();

    final String body = result.getResponse().getContentAsString();
    List<String> values = JsonPath.read(body, "$.values");
    final Set<String> uniqueValues = new HashSet<>(values);

    assertEquals(values.size(), uniqueValues.size(), "Unique values should be unique");
    assertTrue(uniqueValues.containsAll(Set.of(expected)), "not all values are present");
  }

  @Test
  void test_hidden_attribute() throws Exception {
    final String url =
        apiBasePath
            + "/app/default/layer/lyr:pdok-kadaster-bestuurlijkegebieden:Provinciegebied/unique/ligtInLandCode";
    mockMvc
        .perform(get(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
        .andExpect(status().is4xxClientError())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.message").value("Attribute does not exist"));
  }

  @Test
  void layer_without_featuretype() throws Exception {
    final String url =
        apiBasePath
            + "/app/default/layer/lyr:pdok-kadaster-bestuurlijkegebieden:Gemeentegebied/unique/naam";
    mockMvc
        .perform(get(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
        .andExpect(status().is4xxClientError())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.message").value("Layer does not have feature type"));
  }

  @Test
  void attribute_name_required() throws Exception {
    final String url =
        apiBasePath
            + "/app/default/layer/lyr:pdok-kadaster-bestuurlijkegebieden:Gemeentegebied/unique/ ";
    mockMvc
        .perform(get(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
        .andExpect(status().is4xxClientError())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.message").value("Attribute name is required"));
  }

  @ParameterizedTest(
      name =
          "#{index}: should return unique bronhouder from database when filtered on bronhouder: {0}")
  @MethodSource("databaseArgumentsProvider")
  void bronhouder_with_filter_on_bronhouder_unique_values_test(String url, String... expected)
      throws Exception {
    url = apiBasePath + url;
    String cqlFilter = "bronhouder='G0344'";
    if (url.contains("BRONHOUDER")) {
      // uppercase oracle cql filter
      cqlFilter = cqlFilter.toUpperCase(Locale.ROOT);
    }

    MvcResult result =
        mockMvc
            .perform(
                get(url)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(setServletPath(url))
                    .param("filter", cqlFilter))
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.filterApplied").value(true))
            .andExpect(jsonPath("$.values").isArray())
            .andExpect(jsonPath("$.values").isNotEmpty())
            .andReturn();

    final String body = result.getResponse().getContentAsString();
    List<String> values = JsonPath.read(body, "$.values");

    assertEquals(1, values.size(), "there should only be 1 value");
    assertTrue(List.of(expected).contains(values.get(0)), "not all values are present");
  }

  @ParameterizedTest(
      name =
          "#{index}: should return no unique bronhouder from database with exclusion filter: {0}")
  @MethodSource("databaseArgumentsProvider")
  void bronhouder_with_filter_on_inonderzoek_unique_values_test(String url) throws Exception {
    url = apiBasePath + url;
    String cqlFilter = "inonderzoek=TRUE";
    if (url.contains("BRONHOUDER")) {
      // uppercase oracle cql filter
      cqlFilter = cqlFilter.toUpperCase(Locale.ROOT);
    }

    mockMvc
        .perform(
            get(url)
                .with(setServletPath(url))
                .param("filter", cqlFilter)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.filterApplied").value(true))
        .andExpect(jsonPath("$.values").isArray())
        .andExpect(jsonPath("$.values").isEmpty());
  }

  @RetryingTest(2)
  // https://b3partners.atlassian.net/browse/HTM-758
  void unique_values_from_wfs() throws Exception {
    final String url = apiBasePath + provinciesWfsUrl;
    MvcResult result =
        mockMvc
            .perform(get(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.filterApplied").value(false))
            .andExpect(jsonPath("$.values").isArray())
            .andExpect(jsonPath("$.values").isNotEmpty())
            .andReturn();

    final String body =
        result.getResponse().getContentAsString(/* for Fryslân */ StandardCharsets.UTF_8);
    final List<String> values = JsonPath.read(body, "$.values");
    assertEquals(12, values.size(), "there should be 12 provinces");

    final Set<String> uniqueValues = new HashSet<>(values);
    assertEquals(values.size(), uniqueValues.size(), "Unique values should be unique");
    assertTrue(
        uniqueValues.containsAll(
            Arrays.asList(
                "Noord-Brabant",
                "Zuid-Holland",
                "Utrecht",
                "Groningen",
                "Drenthe",
                "Fryslân",
                "Zeeland",
                "Limburg",
                "Noord-Holland",
                "Gelderland",
                "Flevoland",
                "Overijssel")),
        "not all values are present");

    final List<String> sortedValues = values.stream().sorted().collect(Collectors.toList());
    assertEquals(sortedValues, values, "Unique values should be sorted");
  }

  @RetryingTest(2)
  void unique_values_from_wfs_with_filter_on_same() throws Exception {
    final String url = apiBasePath + provinciesWfsUrl;
    final String cqlFilter = "naam='Utrecht'";
    mockMvc
        .perform(
            get(url)
                .accept(MediaType.APPLICATION_JSON)
                .with(setServletPath(url))
                .param("filter", cqlFilter))
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.filterApplied").value(true))
        .andExpect(jsonPath("$.values").isArray())
        .andExpect(jsonPath("$.values.length()").value(1))
        .andExpect(jsonPath("$.values[0]").value("Utrecht"));
  }

  @RetryingTest(2)
  void unique_values_from_wfs_with_filter_on_different() throws Exception {
    String cqlFilter = "naam like '%Holland'";
    final String url = apiBasePath + provinciesWfsUrl;
    mockMvc
        .perform(
            get(url)
                .accept(MediaType.APPLICATION_JSON)
                .with(setServletPath(url))
                .param("filter", cqlFilter))
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.filterApplied").value(true))
        .andExpect(jsonPath("$.values").isArray())
        .andExpect(jsonPath("$.values.length()").value(2))
        .andExpect(jsonPath("$.values[0]").value(Matchers.containsString("-Holland")))
        .andExpect(jsonPath("$.values[1]").value(Matchers.containsString("-Holland")));
  }

  /**
   * Testcase for <a href="https://b3partners.atlassian.net/browse/HTM-492">HTM-492</a> where
   * Jackson fails to process oracle.sql.TIMESTAMP.
   *
   * <p>The exception is: {@code org.springframework.web.util.NestedServletException: Request
   * processing failed; nested exception is java.lang.ClassCastException: class oracle.sql.TIMESTAMP
   * cannot be cast to class java.lang.Comparable (oracle.sql.TIMESTAMP is in unnamed module of
   * loader 'app'; java.lang.Comparable is in module java.base of loader 'bootstrap') }
   *
   * <p>For this testcase to go green set the environment variable {@code
   * -Doracle.jdbc.J2EE13Compliant=true}
   *
   * <p>See also:
   *
   * <ul>
   *   <li><a
   *       href="https://stackoverflow.com/questions/13269564/java-lang-classcastexception-oracle-sql-timestamp-cannot-be-cast-to-java-sql-ti">java.lang.ClassCastException:
   *       oracle.sql.TIMESTAMP cannot be cast to java.sql.Timestamp</a>
   *   <li><a
   *       href="https://docs.oracle.com/en/database/oracle/oracle-database/19/jjdbc/accessing-and-manipulating-Oracle-data.html#GUID-C23007CA-E25D-4747-A3C0-4DE219AF56BD">Accessing
   *       and Manipulating Oracle Data</a>
   * </ul>
   *
   * @throws Exception if any
   */
  @Issue("https://b3partners.atlassian.net/browse/HTM-492")
  @Test
  void unique_values_oracle_timestamp_HTM_492() throws Exception {
    final String testUrl =
        apiBasePath
            + "/app/default/layer/lyr:snapshot-geoserver:oracle:WATERDEEL/unique/TIJDSTIPREGISTRATIE";
    mockMvc
        .perform(get(testUrl).accept(MediaType.APPLICATION_JSON).with(setServletPath(testUrl)))
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.filterApplied").value(false))
        .andExpect(jsonPath("$.values").isArray())
        .andExpect(jsonPath("$.values").isNotEmpty());
  }

  @Test
  void test_wms_secured_proxy_not_in_public_app() throws Exception {
    final String testUrl = apiBasePath + layerProxiedWithAuthInPublicApp + "/unique/naam";
    mockMvc
        .perform(get(testUrl).accept(MediaType.APPLICATION_JSON).with(setServletPath(testUrl)))
        .andExpect(status().isForbidden());
  }
}
