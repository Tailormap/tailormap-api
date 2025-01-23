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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.tailormap.api.annotation.PostgresIntegrationTest;

@PostgresIntegrationTest
@AutoConfigureMockMvc
@Execution(ExecutionMode.CONCURRENT)
@Stopwatch
@TestPropertySource(properties = {"tailormap-api.unique.use_geotools_unique_function=false"})
@Order(2)
class UniqueValuesControllerReConfiguredIntegrationTest {
  private static final String provinciesWFSUrl =
      "/app/default/layer/lyr:pdok-kadaster-bestuurlijkegebieden:Provinciegebied/unique/naam";
  private static final String begroeidterreindeelPostgisUrl =
      "/app/default/layer/lyr:snapshot-geoserver:postgis:begroeidterreindeel/unique/bronhouder";
  private static final String waterdeelOracleUrl =
      "/app/default/layer/lyr:snapshot-geoserver:oracle:WATERDEEL/unique/BRONHOUDER";
  private static final String wegdeelSqlserverUrl =
      "/app/default/layer/lyr:snapshot-geoserver:sqlserver:wegdeel/unique/bronhouder";

  @Value("${tailormap-api.base-path}")
  private String apiBasePath;

  @Autowired
  private MockMvc mockMvc;

  /** layer url + bronhouders. */
  static Stream<Arguments> databaseArgumentsProvider() {
    return Stream.of(
        arguments(
            begroeidterreindeelPostgisUrl,
            new String[] {"W0636", "G0344", "L0004", "W0155", "L0001", "P0026", "L0002", "G1904"}),
        arguments(
            waterdeelOracleUrl,
            new String[] {"W0636", "P0026", "L0002", "W0155", "G1904", "G0344", "L0004"}),
        arguments(wegdeelSqlserverUrl, new String[] {"P0026", "G0344", "G1904", "L0004", "L0002"}));
  }

  @ParameterizedTest(name = "#{index}: should return all unique values from database: {0}")
  @MethodSource("databaseArgumentsProvider")
  void bronhouder_unique_values_test(String url, String... expected) throws Exception {
    url = apiBasePath + url;
    MvcResult result = mockMvc.perform(
            get(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
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

  @ParameterizedTest(
      name = "#{index}: should return unique bronhouder from database when filtered on bronhouder: {0}")
  @MethodSource("databaseArgumentsProvider")
  void bronhouder_with_filter_on_bronhouder_unique_values_test(String url, String... expected) throws Exception {
    url = apiBasePath + url;
    String cqlFilter = "bronhouder='G0344'";
    if (url.contains("BRONHOUDER")) {
      // uppercase oracle cql filter
      cqlFilter = cqlFilter.toUpperCase(Locale.ROOT);
    }

    MvcResult result = mockMvc.perform(get(url).accept(MediaType.APPLICATION_JSON)
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

  @ParameterizedTest(name = "#{index}: should return no unique bronhouder from database with exclusion filter: {0}")
  @MethodSource("databaseArgumentsProvider")
  void bronhouder_with_filter_on_inonderzoek_unique_values_test(String url) throws Exception {
    url = apiBasePath + url;
    String cqlFilter = "inonderzoek=TRUE";
    if (url.contains("BRONHOUDER")) {
      // uppercase oracle cql filter
      cqlFilter = cqlFilter.toUpperCase(Locale.ROOT);
    }

    mockMvc.perform(get(url).with(setServletPath(url))
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
    final String url = apiBasePath + provinciesWFSUrl;
    MvcResult result = mockMvc.perform(
            get(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.filterApplied").value(false))
        .andExpect(jsonPath("$.values").isArray())
        .andExpect(jsonPath("$.values").isNotEmpty())
        .andReturn();

    final String body = result.getResponse().getContentAsString(/* for Fryslân */ StandardCharsets.UTF_8);
    final List<String> values = JsonPath.read(body, "$.values");
    assertEquals(12, values.size(), "there should be 12 provinces");

    final Set<String> uniqueValues = new HashSet<>(values);
    assertEquals(values.size(), uniqueValues.size(), "Unique values should be unique");
    assertTrue(
        uniqueValues.containsAll(Arrays.asList(
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
    final String url = apiBasePath + provinciesWFSUrl;
    final String cqlFilter = "naam='Utrecht'";
    mockMvc.perform(get(url).accept(MediaType.APPLICATION_JSON)
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
    final String url = apiBasePath + provinciesWFSUrl;
    mockMvc.perform(get(url).accept(MediaType.APPLICATION_JSON)
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

  @Issue("https://b3partners.atlassian.net/browse/HTM-492")
  @Test
  void unique_values_oracle_timestamp_HTM_492() throws Exception {
    final String testUrl =
        apiBasePath + "/app/default/layer/lyr:snapshot-geoserver:oracle:WATERDEEL/unique/TIJDSTIPREGISTRATIE";
    mockMvc.perform(get(testUrl).accept(MediaType.APPLICATION_JSON).with(setServletPath(testUrl)))
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.filterApplied").value(false))
        .andExpect(jsonPath("$.values").isArray())
        .andExpect(jsonPath("$.values").isNotEmpty());
  }
}
